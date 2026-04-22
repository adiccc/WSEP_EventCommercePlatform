package application;

import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.policy.DefaultDiscountPolicy;
import domain.policy.DefaultPurchasePolicy;
import domain.user.Founder;
import domain.user.IUserRepo;
import domain.user.User;

public class CompanyService {

    private final ICompanyRepo companyRepo;
    private final IUserRepo userRepo;

    public CompanyService(ICompanyRepo companyRepo, IUserRepo userRepo) {
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
    }
    public Response<Company> createProductionCompany(String sessionToken, String companyId, String companyName,
                                                     String email, String phone, String bankAccount) {
        try {
            User user = userRepo.findById(sessionToken);
            if (user == null) {
                return new Response<>(null, "User not found.");
            }
            if (!user.isConnected()) {
                return new Response<>(null, "User must be logged in to create a company.");
            }

            if (companyRepo.existsById(companyId)) {
                return new Response<>(null, "Company ID already exists in the system.");
            }
            if (companyRepo.existsByName(companyName)) {
                return new Response<>(null, "Company name is already taken.");
            }

            if (email == null || !email.contains("@") || phone == null || bankAccount == null) {
                return new Response<>(null, "Invalid contact or bank account information.");
            }
            ContactInfo contactInfo = new ContactInfo(email, phone, bankAccount);

            DefaultPurchasePolicy defaultPurchase = new DefaultPurchasePolicy();
            DefaultDiscountPolicy defaultDiscount = new DefaultDiscountPolicy();

            Company newCompany = new Company(companyId, companyName, user.getUserId(),
                    contactInfo, defaultPurchase, defaultDiscount);

            Founder founderRole = new Founder(companyId);
            user.addRole(founderRole);

            companyRepo.save(newCompany);
            userRepo.save(user);

            return new Response<>(newCompany, "Production company created successfully.");

        } catch (Exception e) {
            return new Response<>(null, "System error occurred: " + e.getMessage());
        }
    }
}