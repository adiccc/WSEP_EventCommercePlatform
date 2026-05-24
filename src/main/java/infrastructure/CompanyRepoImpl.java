package infrastructure;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.CompanyDTO;
import Exception.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
@Repository

public class CompanyRepoImpl implements ICompanyRepo {
    private static final Logger logger = Logger.getLogger(CompanyRepoImpl.class.getName());
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
            logger.info("store: company " + company.getCompanyId() + " inserted successfully");
            return;
        }
        if (currentCompany.getVersion() != company.getVersion()) {
            throw new OptimisticLockingFailureException(
                    "Event " + currentCompany.getCompanyId() + " version mismatch. Expected: " +
                            company.getVersion() + ", but found: " + currentCompany.getVersion()
            );
        }

        Company updatedCompany = new Company(company);
        updatedCompany.setVersion(company.getVersion() + 1);

        boolean replaced = companies.replace(company.getCompanyId(), currentCompany, updatedCompany);

        if (!replaced) {
            logger.warning("store failed: company " + company.getCompanyId() + " version mismatch. Expected: " +
                    company.getVersion() + ", but found: " + currentCompany.getVersion());
            throw new OptimisticLockingFailureException(
                    "Event " + company.getCompanyId() + " was modified concurrently"
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
    public List<CompanyDTO> findByUserRole(int userId) {
        List<CompanyDTO> result = new ArrayList<>();
        for (Company c : companies.values()) {
            if (!"MEMBER".equals(c.getUserRoleName(userId))) {
                result.add(new CompanyDTO(c.getCompanyId(), c.getCompanyName(), c.isActive()));
            }
        }
        logger.info("findByUserRole: found " + result.size() + " companies for userId " + userId);
        return result;
    }

    @Override
    public boolean existsByName(String companyName) {
        boolean exists = companies.values().stream().anyMatch(c -> c.getCompanyName().equals(companyName));
        logger.info("existsByName: company name '" + companyName + "' exists=" + exists);
        return exists;
    }
}
