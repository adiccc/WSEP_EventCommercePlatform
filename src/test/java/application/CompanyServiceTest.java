package application;

import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.ICompanyRepo;
import domain.event.IOrderRepo;
import domain.policy.DiscountPolicy;
import domain.user.IUserRepo;
import domain.event.Order;
import domain.policy.CodeCoupun;
import domain.policy.MaxTicketsRule;
import domain.policy.MinAgeRule;
import domain.policy.PurchasePolicy;
import domain.policy.VisualDiscount;
import infrastructure.CompanyRepoImpl;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyServiceTest {

    private static final int COMPANY_ID = 1;
    private static final int OWNER_ID = 123;
    private static final int OTHER_USER_ID = 456;

    private Company company;
    private static final String OWNER_TOKEN = "owner-token";
    private static final String OTHER_TOKEN = "other-token";

    private CompanyService service;

    @BeforeEach
    void setUp() {
        company = new Company(COMPANY_ID, "Test Company", OWNER_ID,
                new ContactInfo("test@test.com", "0500000000", "bank-1"),
                new PurchasePolicy(), new DiscountPolicy());

        ICompanyRepo companyRepo = new CompanyRepoImpl();
        companyRepo.store(company);

        IAuth auth = new IAuth() {
            @Override public Response<String> login(String username, String password) {
                return Response.ok("generated-token");
            }
            @Override public Response<Boolean> logout(String token) {
                return Response.ok(true);
            }

            @Override public Response<Boolean> isLoggedIn(String token) {
                if(OWNER_TOKEN.equals(token) || OTHER_TOKEN.equals(token)) {
                    return new Response<>(true, "");
                }
                else return new Response<>(false,"");
            }
            @Override public Response<Integer> getUserId(String token) {
                if (OWNER_TOKEN.equals(token)) return new Response<>(OWNER_ID, "");
                return new Response<>(OTHER_USER_ID, "");
            }
        };

        IOrderRepo orderRepo = new IOrderRepo() {
            @Override public Order findById(Integer id) { return null; }
            @Override public List<Order> getAll() { return new ArrayList<>(); }
            @Override public void delete(Integer id) {}
            @Override public void store(Order o) {}
            @Override public int getTicketsBoughtByUserForEvent(int userId, int eventId) { return 0; }
        };

        IUserRepo userRepo = mock(IUserRepo.class);
        service = new CompanyService(auth, companyRepo, userRepo, orderRepo);
    }

    // --- Successful_PurchasePolicy_Set ---

    @Test
    void GivenOwnerAndValidPolicy_WhenUpdatePurchasePolicy_ThenSuccess() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    // --- Successful_PurchasePolicy_Update ---

    @Test
    void GivenOwnerAndExistingPolicy_WhenUpdatePurchasePolicy_ThenPolicyReplaced() {
        PurchasePolicy oldPolicy = new PurchasePolicy();
        oldPolicy.addRule(new MaxTicketsRule(2));
        service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, oldPolicy);

        PurchasePolicy newPolicy = new PurchasePolicy();
        newPolicy.addRule(new MaxTicketsRule(4));
        newPolicy.addRule(new MinAgeRule(18));

        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, newPolicy);

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
        Response<Boolean> response = service.updatePurchasePolicy(OTHER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    // --- Invalid_Policy_Data ---

    @Test
    void GivenNegativeTicketCount_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(-1));
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenUpdatePurchasePolicy_ThenError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(-5));
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    // --- Company_Not_Found ---

    @Test
    void GivenCompanyNotFound_WhenUpdatePurchasePolicy_ThenError() {
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, 999, new PurchasePolicy());
        assertTrue(response.isError());
    }

    // --- Company_Inactive ---

    @Test
    void GivenInactiveCompany_WhenUpdatePurchasePolicy_ThenError() {
        company.deactivate();
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));
        Response<Boolean> response = service.updatePurchasePolicy(OWNER_TOKEN, COMPANY_ID, policy);
        assertTrue(response.isError());
    }

    // ===================== updateDiscountPolicy =====================

    // --- Successful_Discount_Update ---

    @Test
    void GivenOwnerAndValidDiscountPolicy_WhenUpdateDiscountPolicy_ThenSuccess() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenOwnerUpdatesExistingPolicy_WhenUpdateDiscountPolicy_ThenPolicyReplaced() {
        DiscountPolicy oldPolicy = new DiscountPolicy();
        oldPolicy.addDiscount(new VisualDiscount(5, LocalDate.now().plusDays(1)));
        service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, oldPolicy);

        DiscountPolicy newPolicy = new DiscountPolicy();
        newPolicy.addDiscount(new VisualDiscount(20, LocalDate.now().plusDays(1)));
        service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, newPolicy);

        assertFalse(service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, newPolicy).isError());
        assertEquals(newPolicy, company.getDiscountPolicy());
    }

    // --- Company_Not_Found ---

    @Test
    void GivenCompanyNotFound_WhenUpdateDiscountPolicy_ThenError() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OWNER_TOKEN, 999, policy);

        assertTrue(response.isError());
    }

    // --- Unauthorized_Discount_Change ---

    @Test
    void GivenNonOwner_WhenUpdateDiscountPolicy_ThenError() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OTHER_TOKEN, COMPANY_ID, policy);

        assertTrue(response.isError());
    }

    // --- Logged_Out_User_Access ---

    @Test
    void GivenInvalidToken_WhenUpdateDiscountPolicy_ThenError() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy("invalid-token", COMPANY_ID, policy);

        assertTrue(response.isError());
    }

    // --- Invalid_Discount_Data ---

    @Test
    void GivenNegativePercentage_WhenUpdateDiscountPolicy_ThenError() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(-10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertTrue(response.isError());
    }

    @Test
    void GivenEmptyCouponCode_WhenUpdateDiscountPolicy_ThenError() {
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new CodeCoupun("", 10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertTrue(response.isError());
    }

    // --- Company_Inactive ---

    @Test
    void GivenInactiveCompany_WhenUpdateDiscountPolicy_ThenError() {
        company.deactivate();
        DiscountPolicy policy = new DiscountPolicy();
        policy.addDiscount(new VisualDiscount(10, LocalDate.now().plusDays(1)));

        Response<Boolean> response = service.updateDiscountPolicy(OWNER_TOKEN, COMPANY_ID, policy);

        assertTrue(response.isError());
    }
}
