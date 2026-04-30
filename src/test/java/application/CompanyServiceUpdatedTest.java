package application;

import DTO.DiscountDTO;
import DTO.PurchaseRuleDTO;
import Log.LoggerSetup;
import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.dto.UserDTO;
import domain.dto.CompanyDTO;
import domain.user.IUserRepo;
import infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class CompanyServiceUpdatedTest {

    private int COMPANY_ID = 1;
    private int OWNER_ID;
    private int OTHER_USER_ID;

    private String OWNER_TOKEN;
    private String OTHER_TOKEN;

    private CompanyService service;
    private UserService userService;
    private ICompanyRepo companyRepo;
    private IAuth auth;
    private IUserRepo userRepo;

    @BeforeEach
    void setUp() {
        LoggerSetup.setup();
        userRepo = new UserRepo();
        IPasswordEncoder passwordEncoder = new PasswordEncoderUtil();
        TokenService tokenService = new TokenService();
        auth = new Auth(tokenService, userRepo, passwordEncoder);
        companyRepo = new CompanyRepoImpl();

        userService = new UserService(tokenService, auth, userRepo, passwordEncoder);
        service = new CompanyService(auth, companyRepo, userRepo);

        UserDTO ownerDTO = new UserDTO("owner@test.com", "Owner", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, ownerDTO);
        OWNER_TOKEN = userService.login("owner@test.com", "Password123!").getValue();
        OWNER_ID = auth.getUserId(OWNER_TOKEN).getValue();

        UserDTO otherDTO = new UserDTO("other@test.com", "Other", "Test", "Password123!", 1, 1, 2000, "City", "050-123-4567");
        userService.registerUser(null, otherDTO);
        OTHER_TOKEN = userService.login("other@test.com", "Password123!").getValue();
        OTHER_USER_ID = auth.getUserId(OTHER_TOKEN).getValue();

        service.createProductionCompany(OWNER_TOKEN,COMPANY_ID,"Test Company","test@test.com","0500000000","bank-1");
    }

    // ===================== Get Available Companies =====================

    @Test
    void GivenInvalidToken_WhenGetAvailableCompanies_ThenError() {
        Response<List<CompanyDTO>> response = service.getAvailableCompanies("invalid-token");
        assertNull(response.getValue());
        assertEquals("Invalid or expired token", response.getMessage());
    }

    @Test
    void GivenNoCompaniesInSystem_WhenGetAvailableCompanies_ThenError() {
        companyRepo.delete(COMPANY_ID);
        Response<List<CompanyDTO>> response = service.getAvailableCompanies("valid-token");
        assertNull(response.getValue());
        assertEquals("No companies in the system", response.getMessage());
    }

    @Test
    void GivenGuest_WhenActiveAndInactiveCompaniesExist_ThenReturnOnlyActive() {
        service.createProductionCompany(OWNER_TOKEN,2,"Inactive Co","a@b.com","05034445897", "bank-b");
        Response<Boolean> response = service.deactivateCompany(OWNER_TOKEN,2);
        Response<List<CompanyDTO>> response1 = service.getAvailableCompanies(null);

        assertNotNull(response.getValue());
        assertEquals(1, response1.getValue().size());
        assertEquals("Test Company", response1.getValue().get(0).companyName());
    }

    @Test
    void GivenGuest_WhenAllCompaniesAreInactive_ThenErrorNoCompanies() {
        service.deactivateCompany(OWNER_TOKEN,COMPANY_ID);
        Response<List<CompanyDTO>> response = service.getAvailableCompanies(null);
        assertNull(response.getValue());
        assertEquals("No companies in the system", response.getMessage());
    }

    @Test
    void GivenOtherMember_WhenInactiveCompanyExists_ThenReturnOnlyActive() {
        service.createProductionCompany(OWNER_TOKEN, 2, "Inactive Co", "a@b.com", "05034445897", "bank-b");
        service.deactivateCompany(OWNER_TOKEN, 2);

        Response<List<CompanyDTO>> response = service.getAvailableCompanies(OTHER_TOKEN);
        assertNotNull(response.getValue());
        assertEquals(1, response.getValue().size());
        assertEquals("Test Company", response.getValue().get(0).companyName());
    }

    @Test
    void GivenOwner_WhenInactiveCompanyExists_ThenReturnAll() {
        service.createProductionCompany(OWNER_TOKEN, 2, "Inactive Co", "a@b.com", "05034445897", "bank-b");
        service.deactivateCompany(OWNER_TOKEN, 2);

        Response<List<CompanyDTO>> response = service.getAvailableCompanies(OWNER_TOKEN);

        assertNotNull(response.getValue());
        assertEquals(2, response.getValue().size());
    }

    // ===================== Purchase Rule functions =====================

    @Test
    void GivenOwnerAndValidRule_WhenAddRuleToCompany_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenOwnerAddsMultipleRules_WhenAddRuleToCompany_ThenAllSucceed() {
        Response<Boolean> first = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID,
                new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4));
        Response<Boolean> second = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID,
                new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, 18));
        assertFalse(first.isError());
        assertFalse(second.isError());
    }

    @Test
    void GivenInvalidToken_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany("invalid-token", COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OTHER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeTicketCount_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, -1);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativeMinAge_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MIN_AGE, -5);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenAddRuleToCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, 999, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddRuleToCompany_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenOwnerAndExistingRule_WhenRemoveRuleFromCompany_ThenSuccess() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenRuleNotFound_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany("invalid-token", COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        service.addRuleToCompany(OWNER_TOKEN, COMPANY_ID, ruleDTO);
        Response<Boolean> response = service.removeRuleFromCompany(OTHER_TOKEN, COMPANY_ID, ruleDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenRemoveRuleFromCompany_ThenError() {
        PurchaseRuleDTO ruleDTO = new PurchaseRuleDTO(PurchaseRuleDTO.Type.MAX_TICKETS, 4);
        Response<Boolean> response = service.removeRuleFromCompany(OWNER_TOKEN, 999, ruleDTO);
        assertTrue(response.isError());
    }

    // ===================== Discount functions =====================

    @Test
    void GivenOwnerAndValidDiscount_WhenAddDiscountToCompany_ThenSuccess() {
        DiscountDTO discountDTO = new DiscountDTO(20.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertFalse(response.isError());
        assertEquals(Boolean.TRUE, response.getValue());
    }

    @Test
    void GivenExistingDiscount_WhenRemoveDiscountFromCompany_ThenSuccess() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(80.0, endDate));

        Response<Boolean> removeResponse = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        assertFalse(removeResponse.isError());
        assertEquals(Boolean.TRUE, removeResponse.getValue());
    }

    @Test
    void GivenCompanyNotFound_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, 999, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenCompanyNotFound_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, 999, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OTHER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNonOwner_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        Response<Boolean> response = service.removeDiscountFromCompany(OTHER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany("invalid-token", COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInvalidToken_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany("invalid-token", COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenNegativePercentage_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(-10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenEmptyCouponCode_WhenAddDiscountToCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO("", 10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenAddDiscountToCompany_ThenError() {
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    @Test
    void GivenInactiveCompany_WhenRemoveDiscountFromCompany_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(20.0, endDate));
        service.deactivateCompany(OWNER_TOKEN, COMPANY_ID);

        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(10.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenDuplicateDiscount_WhenAddDiscountToCompany_ThenError() {
        LocalDate endDate = LocalDate.now().plusDays(1);
        service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(15.0, endDate));
        Response<Boolean> response = service.addDiscountToCompany(OWNER_TOKEN, COMPANY_ID, new DiscountDTO(15.0, endDate));
        assertTrue(response.isError());
    }

    @Test
    void GivenDiscountDoesNotExist_WhenRemoveDiscountFromCompany_ThenError() {
        DiscountDTO discountDTO = new DiscountDTO(10.0, LocalDate.now().plusDays(1));
        Response<Boolean> response = service.removeDiscountFromCompany(OWNER_TOKEN, COMPANY_ID, discountDTO);
        assertTrue(response.isError());
    }

    // ===================== II.3.2 Create Production Company =====================

    @Test
    void GivenValidInputs_WhenCreateProductionCompany_ThenReturnSuccessAndCompanyStored() {
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 99, "NewCo", "new@co.com", "050-999-9999", "bank-99");

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getValue());
        assertEquals("NewCo", response.getValue().getCompanyName());
        assertEquals("NewCo", companyRepo.findById(99).getCompanyName());
        assertTrue(companyRepo.findById(99).isOwner(OWNER_ID));
    }

    @Test
    void GivenDuplicateCompanyId_WhenCreateProductionCompany_ThenReturnError() {
        // COMPANY_ID=1 already created in setUp
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, COMPANY_ID, "Other", "o@o.com", "050-000-0001", "bank-x");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already exists"));
    }

    @Test
    void GivenDuplicateCompanyName_WhenCreateProductionCompany_ThenReturnError() {
        // "Test Company" already created in setUp
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 98, "Test Company", "o@o.com", "050-000-0002", "bank-y");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("already taken"));
    }

    @Test
    void GivenLoggedOutUser_WhenCreateProductionCompany_ThenReturnError() {
        auth.logout(OWNER_TOKEN);

        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 97, "LoggedOutCo", "lo@co.com", "050-000-0003", "bank-z");

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenInvalidEmail_WhenCreateProductionCompany_ThenReturnError() {
        Response<Company> response = service.createProductionCompany(
                OWNER_TOKEN, 96, "BadEmailCo", "not-an-email", "050-000-0004", "bank-w");

        assertTrue(response.isError());
        assertNull(response.getValue());
        assertTrue(response.getMessage().contains("Invalid contact"));
    }

    // ===================== II.4.15 View Roles and Permissions Tree =====================

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenReturnsTree() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertFalse(response.isError(), "Expected success but got: " + response.getMessage());
        assertNotNull(response.getValue());
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenFounderIdIsCorrect() {
        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID).getValue();

        assertEquals(OWNER_ID, tree.getFounderId(),
                "Founder should be the user who created the company");
    }

    @Test
    void GivenOwnerAndValidCompany_WhenViewRolesTree_ThenOwnerSetContainsFounder() {
        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID).getValue();

        assertTrue(tree.getOwnerIds().contains(OWNER_ID));
    }

    @Test
    void GivenNewCompany_WhenViewRolesTree_ThenManagersMapIsEmpty() {
        domain.dto.RolesPermissionsTreeDTO tree =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID).getValue();

        assertTrue(tree.getManagersPermissions().isEmpty(),
                "A brand-new company has no managers");
    }

    @Test
    void GivenNonOwner_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OTHER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenUnknownCompanyId_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, 9999);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenInvalidToken_WhenViewRolesTree_ThenError() {
        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree("not-a-real-token", COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    @Test
    void GivenLoggedOutUser_WhenViewRolesTree_ThenError() {
        auth.logout(OWNER_TOKEN);

        Response<domain.dto.RolesPermissionsTreeDTO> response =
                service.viewRolesAndPermissionsTree(OWNER_TOKEN, COMPANY_ID);

        assertTrue(response.isError());
        assertNull(response.getValue());
    }

    // ===================== Concurrency: Create Production Company =====================

    @Test
    void GivenTwoThreadsRaceWithSameCompanyId_WhenCreateProductionCompany_ThenOnlyOneSucceeds() throws Exception {
        int racingId = 50;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Company>> f1 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OWNER_TOKEN, racingId, "CompanyA", "a@a.com", "050-111-0001", "bank-a");
        });
        Future<Response<Company>> f2 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OTHER_TOKEN, racingId, "CompanyB", "b@b.com", "050-111-0002", "bank-b");
        });

        start.countDown();

        Response<Company> r1 = f1.get();
        Response<Company> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Exactly one thread should successfully create the company");
        assertEquals(1, failed,  "The losing thread should receive a duplicate-ID error");
    }

    @Test
    void GivenTwoThreadsRaceWithSameCompanyName_WhenCreateProductionCompany_ThenOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Response<Company>> f1 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OWNER_TOKEN, 60, "RacingName", "c@c.com", "050-222-0001", "bank-c");
        });
        Future<Response<Company>> f2 = executor.submit(() -> {
            start.await();
            return service.createProductionCompany(OTHER_TOKEN, 61, "RacingName", "d@d.com", "050-222-0002", "bank-d");
        });

        start.countDown();

        Response<Company> r1 = f1.get();
        Response<Company> r2 = f2.get();
        executor.shutdown();

        int success = 0;
        int failed  = 0;
        if (!r1.isError()) success++; else failed++;
        if (!r2.isError()) success++; else failed++;

        assertEquals(1, success, "Only one company with the same name should be created");
        assertEquals(1, failed,  "The losing thread should receive a duplicate-name error");
    }
}
