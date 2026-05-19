package domain.user;

import domain.IRepo;

import java.util.List;

public interface IUserRepo extends IRepo<Member, Integer> {
    boolean existsUser(String email);
    Member findUserByEmail(String email);
    Member findById(Integer userId);
    List<Member> getAll();
    void delete(Integer userId);
    void store(Member mem);
    String getUserEmail(Integer userId);
}
