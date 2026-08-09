package com.example.cachefileapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Simulates outbound email notifications for product lifecycle events.
 */
@Service
@Slf4j
public class NotificationService {

    /**
     * Simulates sending a "product created" email notification asynchronously.
     *
     * @param productName the name of the newly created product
     */
    @Async
    public void sendProductCreatedNotification(String productName) {
        log.info("[{}] Sending product-created notification for '{}'",
                Thread.currentThread().getName(), productName);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] Product-created notification interrupted for '{}'",
                    Thread.currentThread().getName(), productName);
            return;
        }

        log.info("[{}] Product-created notification sent for '{}'",
                Thread.currentThread().getName(), productName);
    }
}
