package domain.company;

import java.util.List;

public interface ICompanyRepo {
    void store(Company company);
    void delete(int companyId);
    Company findById(int companyId);
    List<Company> getAll();
    boolean existsByName(String companyName);
}
