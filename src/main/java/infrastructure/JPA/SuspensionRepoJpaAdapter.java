package infrastructure.JPA;

import domain.Suspension.ISuspensionRepo;
import domain.Suspension.Suspension;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;

@Repository
@Profile("db")
public class SuspensionRepoJpaAdapter implements ISuspensionRepo {

    private final SuspensionJpaRepository suspensionJpaRepository;

    public SuspensionRepoJpaAdapter(SuspensionJpaRepository suspensionJpaRepository) {
        this.suspensionJpaRepository = suspensionJpaRepository;
    }

    @Override
    public Suspension findById(Long suspensionId) {
        return suspensionJpaRepository.findById(suspensionId)
                .orElseThrow(() -> new NoSuchElementException("No suspension found with suspension id " + suspensionId));
    }

    @Override
    public List<Suspension> getAll() {
        return suspensionJpaRepository.findAll();
    }

    @Override
    public void store(Suspension suspension) {
        try {
            suspensionJpaRepository.save(suspension);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockingFailureException(
                    "Suspension " + suspension.getSuspensionId() + " was modified concurrently");
        }
    }

    @Override
    public void delete(Long suspensionId) {
        suspensionJpaRepository.deleteById(suspensionId);
    }

    @Override
    public boolean haveActiveSuspension(Integer userId) {
        return suspensionJpaRepository.findByUserIdOrderByStartTimeDesc(userId)
                .stream()
                .anyMatch(Suspension::isActive);
    }

    @Override
    public Suspension findLastSuspensionByUserId(Integer userId) {
        List<Suspension> userSuspensions = suspensionJpaRepository.findByUserIdOrderByStartTimeDesc(userId);
        if (userSuspensions.isEmpty()) {
            throw new NoSuchElementException("No suspension found with user id " + userId);
        }
        return userSuspensions.stream()
                .findFirst()
                .orElse(null);
    }
}