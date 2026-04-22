package domain.company;

import domain.policy.MaxTicketsRule;
import domain.policy.MinAgeRule;
import domain.policy.PurchasePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompanyTest {

    private Company company;
    private static final int OWNER_ID = 123;
    private static final int OTHER_USER_ID = 456;

    @BeforeEach
    void setUp() {
        company = new Company(1, "Company 001", OWNER_ID);
    }

    // --- Successful_PurchasePolicy_Set ---

    @Test
    void GivenOwnerAndValidPolicy_WhenUpdatePurchasePolicy_ThenPolicySaved() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        String result = company.updatePurchasePolicy(OWNER_ID, policy);

        assertNull(result);
        assertEquals(policy, company.getPurchasePolicy());
    }

    // --- Successful_PurchasePolicy_Update ---

    @Test
    void GivenOwnerAndExistingPolicy_WhenUpdatePurchasePolicy_ThenPolicyReplaced() {
        PurchasePolicy oldPolicy = new PurchasePolicy();
        oldPolicy.addRule(new MaxTicketsRule(2));
        company.updatePurchasePolicy(OWNER_ID, oldPolicy);

        PurchasePolicy newPolicy = new PurchasePolicy();
        newPolicy.addRule(new MaxTicketsRule(4));
        newPolicy.addRule(new MinAgeRule(18));

        String result = company.updatePurchasePolicy(OWNER_ID, newPolicy);

        assertNull(result);
        assertEquals(newPolicy, company.getPurchasePolicy());
    }

    // --- Unauthorized_Policy_Change ---

    @Test
    void GivenNonOwnerUser_WhenUpdatePurchasePolicy_ThenReturnError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        String result = company.updatePurchasePolicy(OTHER_USER_ID, policy);

        assertNotNull(result);
        assertNotEquals(policy, company.getPurchasePolicy());
    }

    // --- Invalid_Policy_Data ---

    @Test
    void GivenNegativeTicketCount_WhenUpdatePurchasePolicy_ThenReturnError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(-1));

        String result = company.updatePurchasePolicy(OWNER_ID, policy);

        assertNotNull(result);
    }

    @Test
    void GivenNegativeMinAge_WhenUpdatePurchasePolicy_ThenReturnError() {
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MinAgeRule(-5));

        String result = company.updatePurchasePolicy(OWNER_ID, policy);

        assertNotNull(result);
    }

    // --- Company inactive ---

    @Test
    void GivenInactiveCompany_WhenUpdatePurchasePolicy_ThenReturnError() {
        company.deactivate();
        PurchasePolicy policy = new PurchasePolicy();
        policy.addRule(new MaxTicketsRule(4));

        String result = company.updatePurchasePolicy(OWNER_ID, policy);

        assertNotNull(result);
    }

    // --- addPurchaseRule ---

    @Test
    void GivenOwnerAndValidRule_WhenAddPurchaseRule_ThenRuleAdded() {
        String result = company.addPurchaseRule(OWNER_ID, new MaxTicketsRule(4));

        assertNull(result);
        assertEquals(1, company.getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenNonOwnerUser_WhenAddPurchaseRule_ThenReturnError() {
        String result = company.addPurchaseRule(OTHER_USER_ID, new MaxTicketsRule(4));

        assertNotNull(result);
        assertEquals(0, company.getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenInvalidRule_WhenAddPurchaseRule_ThenReturnError() {
        String result = company.addPurchaseRule(OWNER_ID, new MaxTicketsRule(0));

        assertNotNull(result);
        assertEquals(0, company.getPurchasePolicy().getRules().size());
    }

    @Test
    void GivenInactiveCompany_WhenAddPurchaseRule_ThenReturnError() {
        company.deactivate();

        String result = company.addPurchaseRule(OWNER_ID, new MaxTicketsRule(4));

        assertNotNull(result);
        assertEquals(0, company.getPurchasePolicy().getRules().size());
    }
}
