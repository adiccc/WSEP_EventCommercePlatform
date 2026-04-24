package infrastructure;

import application.Response;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserRepo implements IUserRepo {
    private final ConcurrentHashMap<Integer, Member> usersPerId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> emailById = new ConcurrentHashMap<>();
    private final AtomicInteger userIdGenerator = new AtomicInteger(1);

    @Override
    public void store(Member mem) {
        if(mem.getUserId() == null) {
            int id = userIdGenerator.getAndIncrement();
            mem.setUserId(id);
        }
            usersPerId.put(mem.getUserId(), mem);
            emailById.put(mem.getIdentifier(), mem.getUserId());

    }
    @Override
    public boolean existsUser(String email){
        return emailById.containsKey(email);
    }
    @Override
    public Member findUserByEmail(String email){
        return usersPerId.get(emailById.get(email));
    }

    @Override
    public Member findById(Integer userId) {
        return usersPerId.get(userId);
    }

    @Override
    public List<Member> getAll(){
        return new ArrayList<>(usersPerId.values());
    }

    @Override
    public void delete(Integer userId) {
        Member member = usersPerId.get(userId);
        emailById.remove(member.getIdentifier());
        usersPerId.remove(userId);
    }

}
