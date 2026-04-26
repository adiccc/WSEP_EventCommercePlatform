package Exception;

// Custom exception to indicate a version mismatch during concurrent updates
public class OptimisticLockingFailureException extends RuntimeException {
    public OptimisticLockingFailureException(String message) {
        super(message);
    }
}