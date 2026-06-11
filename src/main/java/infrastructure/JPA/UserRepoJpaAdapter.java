package infrastructure.JPA;

import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;

@Repository
@Profile("user-db")
public class UserRepoJpaAdapter implements IUserRepo {

    private final UserJpaRepository userJpaRepository;

    public UserRepoJpaAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public boolean existsUser(String email) {
        return userJpaRepository.findByIdentifier(email).isPresent();
    }

    @Override
    public Member findUserByEmail(String email) {
        return userJpaRepository.findByIdentifier(email).orElse(null);
    }

    @Override
    public Member findById(Integer userId) {
        return userJpaRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));
    }

    @Override
    public List<Member> getAll() {
        return userJpaRepository.findAll();
    }

    @Override
    public void delete(Integer userId) {
        userJpaRepository.deleteById(userId);
    }

    @Override
    public void store(Member mem) {
        try {
            userJpaRepository.save(mem);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "Optimistic locking failure for member: " + mem.getUserId());
        }
    }

    @Override
    public String getUserEmail(Integer userId) {
        Member member = userJpaRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));
        return member.getIdentifier();
    }
}