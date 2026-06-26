package application;

import Exception.OptimisticLockingFailureException;
import app.config.SystemProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class RetryHelper {
    private static final Logger logger = Logger.getLogger(RetryHelper.class.getName());
    private static int maxJitterSleepMs;
    private static int maxRetries;

    @Autowired
    public RetryHelper(SystemProperties systemProperties) {
        maxRetries = systemProperties.getRetryCount();
        maxJitterSleepMs = systemProperties.getRetryJitterMaxMs();
    }

    public static <T> Response<T> executeWithRetry(Callable<Response<T>> action) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Execute the original logic
                return action.call();

            } catch (OptimisticLockingFailureException e) {
                logger.log(Level.WARNING,
                        "Optimistic locking failure detected, retrying... (" + attempt + "/" + maxRetries + ")");

                // If this was the last attempt, fail gracefully
                if (attempt == maxRetries) {
                    return new Response<>(null, "The system is currently busy, please try again later.");
                }

                // Backoff & Jitter: Random sleep to prevent repeated collisions
                sleepWithJitter();

            } catch (CannotCreateTransactionException e) {
                logger.log(Level.WARNING,
                        "Could not create DB transaction, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (QueryTimeoutException e) {
                logger.log(Level.WARNING,
                        "DB query timed out, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (DataAccessResourceFailureException e) {
                logger.log(Level.WARNING,
                        "Database resource failure detected, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (TransientDataAccessException e) {
                logger.log(Level.WARNING,
                        "Transient database error detected, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (TransactionTimedOutException e) {
                logger.log(Level.WARNING,
                        "Transaction timed out, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (NonTransientDataAccessException e) {
                // Non-transient DB errors are not expected to succeed by retrying
                logger.log(Level.SEVERE, "Non-retryable database error: " + e.getMessage());
                return new Response<>(null, "Database error: " + e.getMessage());

            } catch (TransactionException e) {
                logger.log(Level.WARNING,
                        "Transaction infrastructure error detected, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (DataAccessException e) {
                logger.log(Level.WARNING,
                        "Uncategorized database error detected, retrying... (" + attempt + "/" + maxRetries + ")");

                if (attempt == maxRetries) {
                    return new Response<>(null, "Database temporarily unavailable. Please try again later.");
                }

                sleepWithJitter();

            } catch (Exception e) {
                // For any other exception, do not retry
                logger.log(Level.SEVERE, "Unexpected error: " + e.getMessage());
                return new Response<>(null, "System Error: " + e.getMessage());
            }
        }

        return new Response<>(null, "System Error");
    }

    private static void sleepWithJitter() {
        try {
            Thread.sleep((long) (Math.random() * maxJitterSleepMs));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}