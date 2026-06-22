package domain.company;

import DTO.CompanyDTO;

import java.util.List;

public interface ICompanyRepo {
    void store(Company company);
    void delete(int companyId);
    Company findById(int companyId);
    List<Company> getAll();
    boolean existsByName(String companyName);

    /** Returns companies where the given user holds a role (FOUNDER, OWNER, or MANAGER). */
    List<CompanyDTO> findByUserRole(int userId);
}
