package application;

import Exception.OptimisticLockingFailureException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RetryHelper {
    private static final Logger logger = Logger.getLogger(RetryHelper.class.getName());
    private static final int MAX_RETRIES = 3;

    public static <T> Response<T> executeWithRetry(Callable<Response<T>> action) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Execute the original logic
                return action.call();
            } catch (OptimisticLockingFailureException e) {
                logger.log(Level.WARNING, "Collision detected, retrying... (" + attempt + "/" + MAX_RETRIES + ")");

                // If this was the last attempt, fail gracefully
                if (attempt == MAX_RETRIES) {
                    return new Response<>(null, "The system is currently busy, please try again later.");
                }

                // Backoff & Jitter: Random sleep to prevent repeated collisions
                try {
                    Thread.sleep((long) (Math.random() * 50));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // For any other exception, do not retry
                logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage());
                return new Response<>(null, "System Error: " + e.getMessage());
            }
        }
        return new Response<>(null, "System Error");
    }
}