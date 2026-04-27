package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;
import Exception.OptimisticLockingFailureException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CompanyRepoImpl implements ICompanyRepo {
    private static final Logger logger = Logger.getLogger(CompanyRepoImpl.class.getName());
    private ConcurrentHashMap<Integer, Company> companies;

    public CompanyRepoImpl() {
        this.companies = new ConcurrentHashMap<>();
    }

    @Override
    public void store(Company company) {
        if (company.getVersion() == 0) {
            // New company — insert only, never fall through to update path
            Company newEntry = new Company(company);
            Company existing = companies.putIfAbsent(newEntry.getCompanyId(), newEntry);
            if (existing != null) {
                logger.warning("store failed: company " + company.getCompanyId() + " was already created concurrently");
                throw new OptimisticLockingFailureException(
                        "Company " + company.getCompanyId() + " was already created concurrently");
            }
            logger.info("store: company " + company.getCompanyId() + " inserted successfully");
            return;
        }

        // Existing company — update with version check
        Company currentCompany = companies.get(company.getCompanyId());
        if (currentCompany == null) {
            logger.warning("store failed: company " + company.getCompanyId() + " not found for update");
            throw new NoSuchElementException("Company " + company.getCompanyId() + " not found for update");
        }

        Company updatedCompany = new Company(company);
        updatedCompany.setVersion(company.getVersion() + 1);

        boolean replaced = companies.replace(company.getCompanyId(), currentCompany, updatedCompany);

        if (!replaced) {
            logger.warning("store failed: company " + company.getCompanyId() + " version mismatch. Expected: " +
                    company.getVersion() + ", but found: " + currentCompany.getVersion());
            throw new OptimisticLockingFailureException(
                    "Company " + company.getCompanyId() + " version mismatch. Expected: " +
                            company.getVersion() + ", but found: " + currentCompany.getVersion()
            );
        }
        logger.info("store: company " + company.getCompanyId() + " updated to version " + updatedCompany.getVersion());
    }

    @Override
    public void delete(int companyId) {
        companies.remove(companyId);
        logger.info("delete: company " + companyId + " removed");
    }

    @Override
    public Company findById(int companyId) {
        Company dbCompany = companies.get(companyId);
        if (dbCompany != null) {
            logger.info("findById: company " + companyId + " found");
            return new Company(dbCompany);
        }
        logger.warning("findById: company " + companyId + " not found");
        throw new NoSuchElementException("Company with id " + companyId + " not found");
    }

    @Override
    public List<Company> getAll() {
        List<Company> copies = new ArrayList<>();
        for (Company c : companies.values()) {
            copies.add(new Company(c));
        }
        logger.info("getAll: returned " + copies.size() + " companies");
        return copies;
    }

    @Override
    public boolean existsByName(String companyName) {
        boolean exists = companies.values().stream().anyMatch(c -> c.getCompanyName().equals(companyName));
        logger.info("existsByName: company name '" + companyName + "' exists=" + exists);
        return exists;
    }
}
