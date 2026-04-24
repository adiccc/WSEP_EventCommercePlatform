package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;

import java.util.concurrent.ConcurrentHashMap;

public class ConcreteCompanyRepo implements ICompanyRepo {

    private final ConcurrentHashMap<String, Company> companiesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> companiesByName = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Company company) {
        companiesById.put(company.getCompanyId(), company);
        companiesByName.put(company.getCompanyName(), true);
    }

    @Override
    public boolean existsById(String companyId) {
        return companiesById.containsKey(companyId);
    }

    @Override
    public boolean existsByName(String companyName) {
        return companiesByName.containsKey(companyName);
    }
}
