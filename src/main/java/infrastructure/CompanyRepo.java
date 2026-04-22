package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompanyRepo implements ICompanyRepo {
    private final Map<Integer, Company> store = new HashMap<>();

    @Override
    public Company findById(Integer id) {
        Company company = store.get(id);
        if (company == null)
            throw new IllegalArgumentException("Company not found: " + id);
        return company;
    }

    @Override
    public List<Company> getAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(Integer id) {
        store.remove(id);
    }

    @Override
    public void store(Company company) {
        store.put(company.getCompanyId(), company);
    }
}
