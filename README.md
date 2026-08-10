# Cache, File Management & Asynchronous Processing API

A Spring Boot REST API demonstrating production-oriented features: Redis-backed caching with proper invalidation, secure file upload/download, scheduled cleanup tasks, and non-blocking asynchronous processing.

## Tech Stack
- Java 21
- Spring Boot 3.3.2
- Spring Data JPA
- MySQL (Docker)
- Redis (Docker) — via Spring Cache abstraction
- Spring Scheduling (`@Scheduled`)
- Spring Async (`@Async`)
- springdoc-openapi (Swagger UI)
- Lombok

## Prerequisites
- Java 21
- Maven
- Docker (for MySQL and Redis)

## Installation & Setup

1. **Clone the repository**
```bash
   git clone <repository-url>
   cd cache-file-async-api
```

2. **Start MySQL via Docker**
```bash
   docker run --name cache-mysql -e MYSQL_ROOT_PASSWORD=root1234 -e MYSQL_DATABASE=cache_file_db -p 3308:3306 -d mysql:8.0
```

3. **Start Redis via Docker**
```bash
   docker run --name cache-redis -p 6379:6379 -d redis:7
```

4. **Set Environment Variables**
   Configure the following (or refer to `.env.example`):
   - `DB_URL`: `jdbc:mysql://localhost:3308/cache_file_db`
   - `DB_USERNAME`: `root`
   - `DB_PASSWORD`: `root1234`
   - `REDIS_HOST`: `localhost`
   - `REDIS_PORT`: `6379`
   - `SPRING_PROFILES_ACTIVE`: `dev` (default if unset) or `prod`

5. **Run the Application**
```bash
   ./mvnw spring-boot:run
```
   The app runs under context path `/api` (e.g. `http://localhost:8080/api/products`).

## API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/products` | List all products (cached) |
| GET | `/products/{id}` | Get product by ID (cached) |
| POST | `/products` | Create a product (triggers async notification) |
| PUT | `/products/{id}` | Update a product (cache updated in place) |
| DELETE | `/products/{id}` | Delete a product (cache evicted) |
| POST | `/products/{id}/image` | Upload/replace product image (multipart) |
| GET | `/products/{id}/image` | Download product image |
| POST | `/admin/cleanup` | Manually trigger orphaned-file cleanup (testing/ops utility) |

## API Documentation (Swagger UI)

Once the application is running:
- **Swagger UI:** [http://localhost:8080/api/swagger-ui/index.html](http://localhost:8080/api/swagger-ui/index.html)
- **OpenAPI JSON:** [http://localhost:8080/api/v3/api-docs](http://localhost:8080/api/v3/api-docs)

## Key Implementation Notes

### Redis Caching
Product reads (`getAllProducts`, `getProductById`) are annotated with `@Cacheable`, backed by Redis via `RedisCacheManager` with per-cache TTLs (`products`: 15 min, `products-list`: 5 min) and JSON serialization for readability. Write operations use `@CachePut` (update — refreshes the per-id cache entry immediately with fresh data) combined with `@CacheEvict` (invalidates the `products-list` cache, since the list is now stale) via Spring's `@Caching` composite annotation. Delete evicts both caches. This was verified end-to-end: repeated GETs after a cache miss hit Redis directly (no DB query logged), and updates immediately reflect fresh data on the next GET rather than serving stale cached values.

### Secure File Upload
File uploads are validated in multiple layers, not just by trusting client input:
1. File is not empty and doesn't exceed the configured size limit (5 MB).
2. File extension is checked against an allowlist (jpg, jpeg, png, gif, webp).
3. **Magic-byte (file signature) verification** — the actual file content is inspected (e.g. PNG must start with `89 50 4E 47`, JPEG with `FF D8 FF`) rather than trusting the client-declared `Content-Type` header or file extension alone. This was deliberately tested: a plain-text file renamed to `.jpg` with a forged `image/jpeg` Content-Type header was **accepted** when only extension/declared-type checks were in place, and correctly **rejected with 400** once magic-byte validation was added. Files are stored under a generated UUID filename (never the original filename) to avoid path traversal and collisions.

### Scheduled Cleanup
`FileCleanupService` runs on a configurable cron schedule (`app.cleanup.cron`, default 2 AM daily in prod) and removes files in the upload directory that are no longer referenced by any product's `imageFileName` in the database. A manual trigger endpoint (`POST /admin/cleanup`) allows on-demand testing without waiting for the scheduled time. Verified by placing an orphaned file in the uploads directory, triggering cleanup, and confirming only the file referenced by an actual product remained afterward.

### Async Notifications
`NotificationService.sendProductCreatedNotification()` is annotated with `@Async`, backed by a dedicated `ThreadPoolTaskExecutor` (core 2 / max 5 threads) rather than the default single-thread executor. It's called after saving a new product but does not block the HTTP response — verified by timing the `POST /products` request (well under the notification's simulated 3-second delay) and observing that the async log lines appear on a separate thread (`async-1`) several seconds after the request thread had already returned its response.

### Dev/Prod Profiles
`application.yml` holds shared settings only; `application-dev.yml` and `application-prod.yml` hold environment-specific overrides. Dev provides localhost-friendly fallback defaults for DB/Redis credentials and verbose logging (SQL, cache trace) for local debugging, plus a short cleanup interval for easy testing. Prod disables SQL/debug logging and — critically — has **no fallback defaults for secrets** (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST` are required). This was verified: starting with `SPRING_PROFILES_ACTIVE=prod` and no DB environment variables set causes the application to fail immediately at startup with a clear error, rather than silently falling back to insecure local defaults.

## Testing

Manual verification performed during development:
- **Cache hit/miss:** confirmed via `TRACE`-level cache interceptor logs — first GET shows `No cache entry` → DB fetch → `Creating cache entry`; subsequent GET shows `Cache entry found` with no DB query.
- **Cache invalidation:** updated a product and confirmed the next GET returned fresh data (not stale), consistent with cache logs showing `@CachePut` updating the entry in place.
- **File upload security:** uploaded a real PNG (accepted) and a text file renamed to `.jpg` with a forged `image/jpeg` content-type (rejected with 400 once magic-byte validation was added — accepted before, demonstrating the vulnerability the fix addresses).
- **Scheduled cleanup:** manually triggered `POST /admin/cleanup` after placing an orphaned file in the uploads directory; confirmed only files referenced by existing products remained.
- **Async non-blocking behavior:** timed `POST /products` and confirmed the response returned well before the notification's 3-second simulated delay completed, with async work visibly running on a separate thread pool in the logs.
- **Profile fail-fast:** ran with `SPRING_PROFILES_ACTIVE=prod` and no DB credentials set; confirmed the application failed to start with a clear configuration error rather than falling back to defaults.