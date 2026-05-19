package infrastructure;

import application.Response;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import Exception.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository

public class UserRepo implements IUserRepo {
    private final ConcurrentHashMap<Integer, Member> usersPerId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> emailById = new ConcurrentHashMap<>();
    private final AtomicInteger userIdGenerator = new AtomicInteger(1);

    @Override
    public synchronized void store(Member mem) {
        if (mem.getUserId() == null) {
            if (emailById.containsKey(mem.getIdentifier())) {
                throw new RuntimeException("User already exists");
            }
            int id = userIdGenerator.getAndIncrement();
            mem.setUserId(id);
            Member newEntry = new Member(mem);
            usersPerId.put(id, newEntry);
            emailById.put(newEntry.getIdentifier(), id);
            return;
        }

        Member currentMember = usersPerId.get(mem.getUserId());

        if (currentMember == null) {
            Member newEntry = new Member(mem);
            usersPerId.put(newEntry.getUserId(), newEntry);
            emailById.put(newEntry.getIdentifier(), newEntry.getUserId());
            return;
        }
        if (currentMember.getVersion() != mem.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Member " + mem.getUserId() + " version mismatch. Expected: " +
                            mem.getVersion() + ", but found: " + currentMember.getVersion()
            );
        }
        Member updatedMember = new Member(mem);
        updatedMember.setVersion(mem.getVersion() + 1);

        boolean replaced = usersPerId.replace(mem.getUserId(), currentMember, updatedMember);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "Member " + mem.getUserId() + " was modified concurrently"
            );
        }

        emailById.put(updatedMember.getIdentifier(), updatedMember.getUserId());
    }
    @Override
    public boolean existsUser(String email){
        return emailById.containsKey(email);
    }
    @Override
    public Member findUserByEmail(String email) {
        Integer id = emailById.get(email);
        if (id != null) {
            return findById(id);
        }
        return null;
    }
    @Override
    public Member findById(Integer userId) {
        Member dbMember = usersPerId.get(userId);
        if (dbMember != null) {
            return new Member(dbMember);
        }
        throw new NoSuchElementException("User not found with ID: " + userId);
    }
    @Override
    public List<Member> getAll() {
        List<Member> copies = new ArrayList<>();
        for (Member m : usersPerId.values()) {
            copies.add(new Member(m));
        }
        return copies;
    }
    @Override
    public void delete(Integer userId) {
        Member member = usersPerId.get(userId);
        if (member != null) {
            emailById.remove(member.getIdentifier());
            usersPerId.remove(userId);
        }
    }

    @Override
    public String getUserEmail(Integer userId){
        Member member = usersPerId.get(userId);
        if (member != null) {
            return member.getIdentifier();
        }
        throw new NoSuchElementException("User not found with ID: " + userId);
    }

}
