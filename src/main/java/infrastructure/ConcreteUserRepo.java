package infrastructure;

import domain.user.IUserRepo;
import domain.user.User;

import java.util.concurrent.ConcurrentHashMap;

public class ConcreteUserRepo implements IUserRepo {

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    @Override
    public User findById(String userId) {
        return users.get(userId);
    }

    @Override
    public void save(User user) {
        users.put(user.getUserId(), user);
    }
}
