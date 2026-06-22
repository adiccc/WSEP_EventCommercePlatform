package application;

import Exception.OptimisticLockingFailureException;
import app.config.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class RetryHelper {
    private static final Logger logger = Logger.getLogger(RetryHelper.class.getName());
    private static int maxRetries;

    @Autowired
    public RetryHelper(SystemProperties systemProperties) {
        maxRetries = systemProperties.getRetryCount();
    }

    public static <T> Response<T> executeWithRetry(Callable<Response<T>> action) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Execute the original logic
                return action.call();
            } catch (OptimisticLockingFailureException e) {
                logger.log(Level.WARNING, "Collision detected, retrying... (" + attempt + "/" + maxRetries + ")");

                // If this was the last attempt, fail gracefully
                if (attempt == maxRetries) {
                    return new Response<>(null, "The system is currently busy, please try again later.");
                }

                // Backoff & Jitter: Random sleep to prevent repeated collisions
                try {
                    Thread.sleep((long) (Math.random() * 50));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (TransientDataAccessException e) {

                logger.log(Level.WARNING, "Transient database error detected, retrying... (" + attempt + "/" + maxRetries + ")");
                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }
                try {
                    Thread.sleep((long) (Math.random() * 50));
            }catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                }catch (Exception e) {
                // For any other exception, do not retry
                logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage());
                return new Response<>(null, "System Error: " + e.getMessage());
            }
        }
        return new Response<>(null, "System Error");
    }
}