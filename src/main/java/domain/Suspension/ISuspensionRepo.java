package domain.Suspension;

import domain.IRepo;

public interface ISuspensionRepo extends IRepo<Suspension, Long> {

    boolean haveActiveSuspension(Integer userId);

    Suspension findLastSuspensionByUserId(Integer userId);
}