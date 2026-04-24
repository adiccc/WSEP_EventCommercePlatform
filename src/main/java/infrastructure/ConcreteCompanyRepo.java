package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ConcreteCompanyRepo implements ICompanyRepo {

    private final ConcurrentHashMap<Integer, Company> companiesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> companiesByName = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Company company) {
        companiesById.put(company.getCompanyId(), company);
        companiesByName.put(company.getCompanyName(), true);
    }

    @Override
    public synchronized void store(Company company) {
        companiesById.put(company.getCompanyId(), company);
    }

    @Override
    public void delete(int companyId) {
        Company company = companiesById.remove(companyId);
        if (company != null) {
            companiesByName.remove(company.getCompanyName());
        }
    }

    @Override
    public Company findById(int companyId) {
        return companiesById.get(companyId);
    }

    @Override
    public List<Company> getAll() {
        return new ArrayList<>(companiesById.values());
    }

    @Override
    public boolean existsById(int companyId) {
        return companiesById.containsKey(companyId);
    }

    @Override
    public boolean existsByName(String companyName) {
        return companiesByName.containsKey(companyName);
    }
}
