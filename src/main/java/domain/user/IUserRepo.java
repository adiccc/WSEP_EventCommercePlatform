package domain.user;

public interface IUserRepo {
    User findById(String userId);
    void save(User user);
}
