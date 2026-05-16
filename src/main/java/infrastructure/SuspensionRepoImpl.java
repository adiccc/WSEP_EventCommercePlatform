package infrastructure;

import domain.Suspension.ISuspensionRepo;
import domain.user.Suspension;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import Exception.OptimisticLockingFailureException;

public class SuspensionRepoImpl implements ISuspensionRepo {
    ConcurrentHashMap<Integer,List<Integer>> suspensionsIdByUserID = new ConcurrentHashMap<>(); //<User id, List<Susoensions id>>
    ConcurrentHashMap<Integer,Suspension> suspensionsBySusID = new ConcurrentHashMap<>();
    AtomicInteger suspensionIdGenerator=new AtomicInteger(0);

    // find by suspension id
    @Override
    public Suspension findById(Integer integer) {
        if(suspensionsBySusID.containsKey(integer)){
            return new Suspension(suspensionsBySusID.get(integer));
        }
        throw new NoSuchElementException("No suspension found with suspension id " + integer);
    }

    // returns current active suspensions and suspensions that already passed due date
    @Override
    public List<Suspension> getAll() {
        List<Suspension> res = new ArrayList<>();
        for(Suspension s : suspensionsBySusID.values()){
            res.add(new Suspension(s));
        }
        return res;
    }

    //  delete by suspension id
    @Override
    public void delete(Integer integer) {
        Suspension s=suspensionsBySusID.remove(integer);
        if(s!=null){
            List<Integer> suspensionsIds = suspensionsIdByUserID.get(s.getUserId());
            if(suspensionsIds!=null){
                suspensionsIds.remove(integer);
                suspensionsIdByUserID.put(s.getUserId(),suspensionsIds);
            }
        }

    }

    @Override
    public synchronized void store(Suspension entity) {
        Suspension currSuspension =suspensionsBySusID.get(entity.getSuspensionId());
        if(currSuspension == null){
            int id=suspensionIdGenerator.getAndIncrement();
            entity.setId(id);
            Suspension suspension = new Suspension(entity);
            suspensionsBySusID.put(id,suspension);
            List<Integer> userSuspensions = suspensionsIdByUserID.getOrDefault(entity.getUserId(),new ArrayList<>());
            userSuspensions.add(suspension.getSuspensionId());
            suspensionsIdByUserID.put(entity.getUserId(),userSuspensions);
            return;
        }

        if(currSuspension.getVersion() != entity.getVersion()){
            throw new OptimisticLockingFailureException(
                    "Suspension "+entity.getSuspensionId()+" version mismatch. Expected: "+currSuspension.getVersion() + ", but found: "+entity.getVersion()
            );
        }

        Suspension updatedSuspension = new Suspension(entity);
        updatedSuspension.setVersion(entity.getVersion()+1);
        boolean replaced=suspensionsBySusID.replace(entity.getSuspensionId(),currSuspension,updatedSuspension);
        if(!replaced){
            throw  new OptimisticLockingFailureException("Suspension "+entity.getSuspensionId()+" was modified concurrently");
        }
    }


    @Override
    public boolean hasActiveSuspension(int userId) {
        List<Integer> suspensionsIds = suspensionsIdByUserID.get(userId);
        if(suspensionsIds==null){
            return false;
        }
        for(Integer suspensionId : suspensionsIds){
            if(suspensionsBySusID.get(suspensionId).isActive())
                return true;

        }
        return false;
    }
}
