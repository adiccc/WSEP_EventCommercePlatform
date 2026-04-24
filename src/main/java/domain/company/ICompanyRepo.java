package domain.company;

import java.util.List;

public interface ICompanyRepo {
    void save(Company company);
    void store(Company company);
    void delete(int companyId);
    Company findById(int companyId);
    List<Company> getAll();
    boolean existsById(int companyId);
    boolean existsByName(String companyName);
}
