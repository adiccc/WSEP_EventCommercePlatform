package domain.Suspension;

import domain.IRepo;

public interface ISuspensionRepo extends IRepo<Suspension,Integer> {
    public boolean haveActiveSuspension (int userId);

    Suspension findLastSuspensionByUserId(int userId);
}
