package domain.Suspension;

import domain.IRepo;
import domain.user.Suspension;

public interface ISuspensionRepo extends IRepo<Suspension,Integer> {
    public boolean hasActiveSuspension (int userId);

}
