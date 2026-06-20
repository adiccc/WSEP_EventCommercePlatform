package infrastructure.JPA;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.CompanyDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Adapter: bridges the domain interface (ICompanyRepo) to Spring Data JPA.
 *
 * Activated only when the "company-db" profile is on (see application.properties).
 * Otherwise the in-memory CompanyRepoImpl (with @Profile("memory")) handles requests.
 */
@Repository
@Profile("db")
public class CompanyJpaAdapter implements ICompanyRepo {

    private final CompanyJpaRepository jpaRepo;

    public CompanyJpaAdapter(CompanyJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void store(Company company) {
        jpaRepo.save(company);
    }

    @Override
    public void delete(int companyId) {
        jpaRepo.deleteById(companyId);
    }

    @Override
    public Company findById(int companyId) {
        return jpaRepo.findById(companyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Company with id " + companyId + " not found"));
    }

    @Override
    public List<Company> getAll() {
        return jpaRepo.findAll();
    }

    @Override
    public boolean existsByName(String companyName) {
        return jpaRepo.existsByCompanyName(companyName);
    }

    /**
     * Role (FOUNDER/OWNER/MANAGER) is computed from the Permissions tree,
     * not stored in a queryable column. So we load all companies and filter in Java.
     */
    @Override
    public List<CompanyDTO> findByUserRole(int userId) {
        List<CompanyDTO> result = new ArrayList<>();
        for (Company c : jpaRepo.findAll()) {
            if (!"MEMBER".equals(c.getUserRoleName(userId))) {
                result.add(new CompanyDTO(c.getCompanyId(), c.getCompanyName(), c.isActive()));
            }
        }
        return result;
    }
}
