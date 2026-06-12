package infrastructure.JPA;

import domain.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for Company.
 *
 * JpaRepository<Company, Integer> auto-generates:
 *   - save(Company)               → INSERT / UPDATE
 *   - findById(Integer)           → SELECT ... WHERE company_id = ?
 *   - findAll()                   → SELECT *
 *   - deleteById(Integer)         → DELETE WHERE company_id = ?
 *   - existsById(Integer)         → SELECT 1 WHERE company_id = ?
 *
 * Custom derived query:
 *   - existsByCompanyName(String) → Spring parses the method name and
 *                                   generates: SELECT 1 WHERE company_name = ?
 */
public interface CompanyJpaRepository extends JpaRepository<Company, Integer> {

    boolean existsByCompanyName(String companyName);
}
