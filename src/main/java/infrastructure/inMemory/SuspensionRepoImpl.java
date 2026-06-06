package infrastructure.inMemory;

import domain.Suspension.ISuspensionRepo;
import domain.Suspension.Suspension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import Exception.OptimisticLockingFailureException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory & !suspension-db")
public class SuspensionRepoImpl implements ISuspensionRepo {
    ConcurrentHashMap<Integer,List<Long>> suspensionsIdByUserID = new ConcurrentHashMap<>(); //<User id, List<Susoensions id>>
    ConcurrentHashMap<Long,Suspension> suspensionsBySusID = new ConcurrentHashMap<>();
    AtomicLong suspensionIdGenerator=new AtomicLong(0);

    // find by suspension id
    @Override
    public Suspension findById(Long id) {
        if(suspensionsBySusID.containsKey(id)){
            return new Suspension(suspensionsBySusID.get(id));
        }
        throw new NoSuchElementException("No suspension found with suspension id " + id);
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
    public void delete(Long id) {
        Suspension s=suspensionsBySusID.remove(id);
        if(s!=null){
            List<Long> suspensionsIds = suspensionsIdByUserID.get(s.getUserId());
            if(suspensionsIds!=null){
                suspensionsIds.remove(id);
                suspensionsIdByUserID.put(s.getUserId(),suspensionsIds);
            }
        }

    }

    @Override
    public synchronized void store(Suspension entity) {
        Suspension currSuspension =suspensionsBySusID.get(entity.getSuspensionId());
        if(currSuspension == null){
            Long id=suspensionIdGenerator.getAndIncrement();
            entity.setId(id);
            Suspension suspension = new Suspension(entity);
            suspensionsBySusID.put(id,suspension);
            List<Long> userSuspensions = suspensionsIdByUserID.getOrDefault(entity.getUserId(),new ArrayList<>());
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
    public boolean haveActiveSuspension(Integer userId) {
        List<Long> suspensionsIds = suspensionsIdByUserID.get(userId);
        if(suspensionsIds==null){
            return false;
        }
        for(Long suspensionId : suspensionsIds){
            if(suspensionsBySusID.get(suspensionId).isActive())
                return true;

        }
        return false;
    }

    public Suspension findLastSuspensionByUserId(Integer userId) {
        if(!suspensionsIdByUserID.containsKey(userId)){
            throw new NoSuchElementException("No suspension found with user id " + userId);
        }
        Suspension suspension =suspensionsBySusID.get(suspensionsIdByUserID.get(userId).get(0));
        for(Long suspensionId : suspensionsIdByUserID.get(userId)){
            LocalDateTime endTime=suspensionsBySusID.get(suspensionId).getEndDate();
            if(endTime==null || endTime.isAfter(LocalDateTime.now()))
                return new Suspension(suspension);
        }
        return null;

    }
}
