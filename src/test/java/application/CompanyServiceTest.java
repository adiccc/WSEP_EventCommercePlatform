package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.event.Order;
import domain.policy.MaxTicketsRule;
import domain.policy.MinAgeRule;
import domain.policy.PurchasePolicy;
import infrastructure.CompanyRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyServiceTest {

    private static final int COMPANY_ID = 1;
    private static final int OWNER_ID = 123;
    private static final int OTHER_USER_ID = 456;

    private Company company;
    private CompanyService service;
    private TokenService tokenService;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        company = new Company(COMPANY_ID, "Test Company", OWNER_ID);
        tokenService = new TokenService();
        ownerToken = tokenService.generateToken("owner");
        otherToken = tokenService.generateToken("other");

        ICompanyRepo companyRepo = new CompanyRepo();
        companyRepo.store(company);

        IAuth auth = new IAuth() {
            @Override public Response<String> login(String username, String password) {
                return Response.ok(tokenService.generateToken(username));
            }
            @Override public boolean isLoggedIn(String token) {
                return tokenService.validateToken(token);
            }
            @Override public int getUserId(String token) {
                if (token.equals(ownerToken)) return OWNER_ID;
                return OTHER_USER_ID;
            }
        };

        IOrderRepo orderRepo = new IOrderRepo() {
            @Override public Order findById(Integer id) { return null; }
            @Override public List<Order> getAll() { return new ArrayList<>(); }
            @Override public void delete(Integer id) {}
            @Override public void store(Order o) {}
            @Override public int getTicketsBoughtByUserForEvent(int userId, int eventId) { return 0; }
        };

        service = new CompanyService(tokenService, auth, companyRepo, orderRepo);
    }

    // --- Successful_PurchasePolicy_Set ---

    @Test
    void GivenOwnerAndValidPolicy_WhenUpdatePurchasePolicy_ThenSuccess() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, COMPANY_ID, policy);

        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    // --- Successful_PurchasePolicy_Update ---

    @Test
    void GivenOwnerAndExistingPolicy_WhenUpdatePurchasePolicy_ThenPolicyReplaced() {
        PurchasePolicy oldPolicy = new PurchasePolicy();
        oldPolicy.addRule(new MaxTicketsRule(2));
        service.updatePurchasePolicy(ownerToken, COMPANY_ID, oldPolicy);

        PurchasePolicy newPolicy = new PurchasePolicy();
        newPolicy.addRule(new MaxTicketsRule(4));
        newPolicy.addRule(new MinAgeRule(18));

        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, COMPANY_ID, newPolicy);

        assertFalse(response.isError());
        assertEquals(newPolicy, company.getPurchasePolicy());
    }

    // --- Unauthorized_Policy_Change ---

    @Test
    void GivenInvalidToken_WhenUpdatePurchasePolicy_ThenError() {
        Response<Boolean> response = service.updatePurchasePolicy("invalid-token", COMPANY_ID, new PurchasePolicy());
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        Response<Boolean> response = service.updatePurchasePolicy(otherToken, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    // --- Invalid_Policy_Data ---

    @Test
    void GivenNegativeTicketCount_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(-1));
        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(-5));
        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    // --- Company_Not_Found ---

    @Test
    void GivenCompanyNotFound_WhenUpdatePurchasePolicy_ThenError() {
        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, 999, new PurchasePolicy());
        assertTrue(response.isError());
    }

    // --- Company_Inactive ---

    @Test
    void GivenInactiveCompany_WhenUpdatePurchasePolicy_ThenError() {
        company.deactivate();
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        Response<Boolean> response = service.updatePurchasePolicy(ownerToken, COMPANY_ID, policy);
        assertTrue(response.isError());
    }
}
