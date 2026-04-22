package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompanyRepoImpl implements ICompanyRepo  {
    private Map<Integer, Company> companies;
    public CompanyRepoImpl() {
        this.companies =  new HashMap<>();
    }

    @Override
    public List<Company> getAll() {
        return new ArrayList<>(companies.values());
    }

    @Override
    public void delete(Integer id) {
        companies.remove(id);
    }

    @Override
    public void store(Company company) {
        companies.put(company.getCompanyId(), company);
    }

    @Override
    public Company findById(Integer integer) {
        if(companies.containsKey(integer)) {
            return companies.get(integer);
        }
        throw new IllegalArgumentException("Company with id " + integer + " not found");
    }

}
