package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompanyRepoImpl implements ICompanyRepo {
    private Map<Integer, Company> companies;

    public CompanyRepoImpl() {
        this.companies = new HashMap<>();
    }

    @Override
    public void save(Company company) {
        companies.put(company.getCompanyId(), company);
    }

    @Override
    public void store(Company company) {
        companies.put(company.getCompanyId(), company);
    }

    @Override
    public void delete(int companyId) {
        companies.remove(companyId);
    }

    @Override
    public Company findById(int companyId) {
        if (companies.containsKey(companyId)) {
            return companies.get(companyId);
        }
        throw new IllegalArgumentException("Company with id " + companyId + " not found");
    }

    @Override
    public List<Company> getAll() {
        return new ArrayList<>(companies.values());
    }

    @Override
    public boolean existsById(int companyId) {
        return companies.containsKey(companyId);
    }

    @Override
    public boolean existsByName(String companyName) {
        return companies.values().stream().anyMatch(c -> c.getCompanyName().equals(companyName));
    }
}
