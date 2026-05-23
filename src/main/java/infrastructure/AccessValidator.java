package infrastructure;

import application.IAccessValidator;
import domain.Suspension.ISuspensionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccessValidator implements IAccessValidator {
    private ISuspensionRepo suspensionRepo;

    @Autowired
    public AccessValidator(ISuspensionRepo suspensionRepo) {
        this.suspensionRepo = suspensionRepo;
    }

    @Override
    public boolean hasWriteAccess(int userId) {
        return !suspensionRepo.hasActiveSuspension(userId);
    }
}
