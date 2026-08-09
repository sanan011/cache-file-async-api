package com.example.cachefileapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring application context loads without errors.
 *
 * <p>Uses the {@code test} profile so a real MySQL/Redis connection is not required
 * during CI. Add {@code application-test.yml} with an embedded/mock config when needed.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheFileAsyncApiApplicationTests {

    @Test
    void contextLoads() {
        // If the application context fails to start, this test will fail automatically.
    }
}
