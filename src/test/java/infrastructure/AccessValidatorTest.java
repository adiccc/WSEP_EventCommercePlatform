package infrastructure;

import application.IAccessValidator;
import domain.Suspension.ISuspensionRepo;
import domain.Suspension.Suspension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessValidatorTest {

    private ISuspensionRepo suspensionRepo;
    private IAccessValidator accessValidator;

    private final int userId = 1;

    @BeforeEach
    void setUp() {
        suspensionRepo = new SuspensionRepoImpl();
        accessValidator = new AccessValidator(suspensionRepo);
    }

    @Test
    void GivenUserWithoutActiveSuspension_WhenHasWriteAccess_ThenReturnTrue() {
        // Act
        boolean result = accessValidator.hasWriteAccess(userId);

        // Assert
        assertTrue(result);
    }

    @Test
    void GivenUserWithActiveSuspension_WhenHasWriteAccess_ThenReturnFalse() {
        // Arrange
        Suspension suspension = new Suspension(userId);
        suspensionRepo.store(suspension);

        // Act
        boolean result = accessValidator.hasWriteAccess(userId);

        // Assert
        assertFalse(result);
    }

    @Test
    void GivenDifferentUserSuspended_WhenHasWriteAccess_ThenReturnTrue() {
        // Arrange
        int suspendedUserId = 2;
        Suspension suspension = new Suspension(suspendedUserId);
        suspensionRepo.store(suspension);

        // Act
        boolean result = accessValidator.hasWriteAccess(userId);

        // Assert
        assertTrue(result);
    }

    @Test
    void GivenSuspensionDeleted_WhenHasWriteAccess_ThenReturnTrue() {
        // Arrange
        Suspension suspension = new Suspension(userId);
        suspensionRepo.store(suspension);

        int suspensionId = suspension.getSuspensionId();
        suspensionRepo.delete(suspensionId);

        // Act
        boolean result = accessValidator.hasWriteAccess(userId);

        // Assert
        assertTrue(result);
    }
}