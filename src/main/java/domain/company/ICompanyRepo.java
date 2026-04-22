package domain.company;

public interface ICompanyRepo {
    void save(Company company);
    boolean existsById(String companyId);
    boolean existsByName(String companyName);
}