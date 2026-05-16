package domain.Suspension;

import domain.IRepo;

public interface ISuspensionRepo extends IRepo<Suspension,Integer> {
    public boolean hasActiveSuspension (int userId);

    Suspension findLastSuspensionByUserId(int userId);
}
