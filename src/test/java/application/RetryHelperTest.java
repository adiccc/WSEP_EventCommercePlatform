package application;

import Exception.OptimisticLockingFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryHelperTest {

    @Test
    void GivenActionSucceeds_WhenExecuteWithRetry_ThenReturnsValue() {
        Response<String> result = RetryHelper.executeWithRetry(
                () -> new Response<>("hello", "ok"));

        assertEquals("hello", result.getValue());
        assertEquals("ok", result.getMessage());
    }

    @Test
    void GivenActionSucceedsAfterSomeRetries_WhenExecuteWithRetry_ThenReturnsValueAfterRetries() {
        AtomicInteger attempts = new AtomicInteger();
        Response<String> result = RetryHelper.executeWithRetry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new OptimisticLockingFailureException("conflict");
            }
            return new Response<>("ok", "success");
        });

        assertEquals("ok", result.getValue());
        assertEquals(3, attempts.get());
    }

    @Test
    void GivenActionAlwaysThrowsOptimisticLocking_WhenExecuteWithRetry_ThenReturnsSystemBusy() {
        AtomicInteger attempts = new AtomicInteger();
        Response<String> result = RetryHelper.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw new OptimisticLockingFailureException("conflict");
        });

        assertNull(result.getValue());
        assertEquals("The system is currently busy, please try again later.", result.getMessage());
        assertEquals(50, attempts.get());
    }

    @Test
    void GivenActionAlwaysThrowsTransientDataAccess_WhenExecuteWithRetry_ThenReturnsDbUnavailable() {
        AtomicInteger attempts = new AtomicInteger();
        Response<String> result = RetryHelper.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw new TransientDataAccessException("temp db error") {};
        });

        assertNull(result.getValue());
        assertEquals("Database temporarily unavailable. Please try again later.", result.getMessage());
        assertEquals(50, attempts.get());
    }

    @Test
    void GivenActionThrowsTransientThenSucceeds_WhenExecuteWithRetry_ThenReturnsValue() {
        AtomicInteger attempts = new AtomicInteger();
        Response<Integer> result = RetryHelper.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new TransientDataAccessException("transient") {};
            }
            return new Response<>(42, "done");
        });

        assertEquals(42, result.getValue());
        assertEquals(2, attempts.get());
    }

    @Test
    void GivenActionThrowsGenericException_WhenExecuteWithRetry_ThenReturnsSystemError() {
        AtomicInteger attempts = new AtomicInteger();
        Response<String> result = RetryHelper.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("boom");
        });

        assertNull(result.getValue());
        assertTrue(result.getMessage().startsWith("System Error:"));
        assertEquals(1, attempts.get(), "generic exceptions must not be retried");
    }

    @Test
    void GivenActionThrowsIllegalArgument_WhenExecuteWithRetry_ThenReturnsSystemErrorWithoutRetry() {
        AtomicInteger attempts = new AtomicInteger();
        Response<String> result = RetryHelper.executeWithRetry(() -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("bad arg");
        });

        assertNull(result.getValue());
        assertEquals(1, attempts.get(), "non-transient exceptions must not be retried");
    }
}
