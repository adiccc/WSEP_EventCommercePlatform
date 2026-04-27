package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.lottery.Lottery;
import Exception.OptimisticLockingFailureException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CompanyRepoImpl implements ICompanyRepo {
    private ConcurrentHashMap<Integer, Company> companies;

    public CompanyRepoImpl() {
        this.companies = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized void store(Company company) {
        Company currentCompany = companies.get(company.getCompanyId());

        if (currentCompany == null) {
            Company newEntry = new Company(company);
            companies.put(newEntry.getCompanyId(), newEntry);
            return;
        }

        Company updatedCompany = new Company(company);
        updatedCompany.setVersion(company.getVersion() + 1);

        boolean replaced = companies.replace(company.getCompanyId(), currentCompany, updatedCompany);

        if (!replaced) {
            throw new OptimisticLockingFailureException(
                    "Company " + company.getCompanyId() + " version mismatch. Expected: " +
                            company.getVersion() + ", but found: " + currentCompany.getVersion()
            );
        }
    }
    @Override
    public void delete(int companyId) {
        companies.remove(companyId);
    }

    @Override
    public Company findById(int companyId) {
        Company dbCompany = companies.get(companyId);
        if (dbCompany != null) {
            return new Company(dbCompany);
        }
        throw new NoSuchElementException("Company with id " + companyId + " not found");
    }

    @Override
    public List<Company> getAll() {
        List<Company> copies = new ArrayList<>();
        for (Company c : companies.values()) {
            copies.add(new Company(c));
        }
        return copies;
    }

    @Override
    public boolean existsByName(String companyName) {
        return companies.values().stream().anyMatch(c -> c.getCompanyName().equals(companyName));
    }
}
