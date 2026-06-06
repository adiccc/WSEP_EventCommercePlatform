package infrastructure.JPA;

import domain.Suspension.ISuspensionRepo;
import domain.Suspension.Suspension;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("suspension-db")
public class SuspensionRepoJpaAdapter implements ISuspensionRepo {

    private final SuspensionJpaRepository suspensionJpaRepository;

    public SuspensionRepoJpaAdapter(SuspensionJpaRepository suspensionJpaRepository) {
        this.suspensionJpaRepository = suspensionJpaRepository;
    }

    @Override
    public Suspension findById(Long suspensionId) {
        return suspensionJpaRepository.findById(suspensionId).orElse(null);
    }

    @Override
    public List<Suspension> getAll() {
        return suspensionJpaRepository.findAll();
    }

    @Override
    public void store(Suspension suspension) {
        suspensionJpaRepository.save(suspension);
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
        return suspensionJpaRepository.findByUserIdOrderByStartTimeDesc(userId)
                .stream()
                .findFirst()
                .orElse(null);
    }
}