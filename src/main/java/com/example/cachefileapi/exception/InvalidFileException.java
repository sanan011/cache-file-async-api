package com.example.cachefileapi.exception;

/**
 * Thrown by {@link com.example.cachefileapi.service.FileStorageService} when an uploaded file
 * fails validation — e.g. empty file, unsupported extension, non-image content type,
 * invalid image magic bytes, or exceeds the maximum allowed size.
 *
 * <p>Handled by {@link GlobalExceptionHandler} which maps it to a
 * {@code 400 Bad Request} response with a descriptive message.</p>
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
