package infrastructure;

import application.IAccessValidator;
import domain.Suspension.ISuspensionRepo;
import domain.user.Suspension;

public class AccessValidator implements IAccessValidator {
    private ISuspensionRepo suspensionRepo;

    public AccessValidator(ISuspensionRepo suspensionRepo) {
        this.suspensionRepo = suspensionRepo;
    }

    @Override
    public boolean hasWriteAccess(int userId) {
        return !suspensionRepo.hasActiveSuspension(userId);
    }
}
