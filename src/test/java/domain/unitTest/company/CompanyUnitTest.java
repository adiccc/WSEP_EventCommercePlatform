package domain.unitTest.company;

import domain.company.Company;
import domain.company.ContactInfo;
import domain.company.Permissions;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompanyUnitTest {

    private static final int FOUNDER_ID  = 1;
    private static final int MANAGER_ID  = 3;
    private static final int STRANGER_ID = 99;
    private static final int COMPANY_ID  = 10;

    private Permissions permissions;
    private Company     company;

    @BeforeEach
    void setUp() {
        permissions = new Permissions(FOUNDER_ID);
        company = new Company(COMPANY_ID, "TestCo",
                new ContactInfo("info@test.com", "050-000-0000", "bank-1"),
                new PurchasePolicy(), new DiscountPolicy(), permissions);
    }

    @Test
    void GivenNewPermissions_WhenCreated_ThenFounderIsOwner() {
        assertEquals(FOUNDER_ID, permissions.getFounderId());
        assertTrue(permissions.isOwner(FOUNDER_ID));
    }

    @Test
    void GivenStranger_WhenIsOwnerCalled_ThenReturnsFalse() {
        assertFalse(permissions.isOwner(STRANGER_ID));
    }

    @Test
    void GivenManagerWithPermission_WhenCheckPermission_ThenTrue() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID,
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY)
        );
        assertTrue(permissions.checkPermission(MANAGER_ID, PermissionType.MANAGE_EVENTS_INVENTORY));
    }

    @Test
    void GivenManagerWithoutPermission_WhenCheckPermission_ThenFalse() {
        permissions.getCompanyTree().put(MANAGER_ID, new HierarchyDTO(
                FOUNDER_ID, new ArrayList<>(),
                EnumSet.of(PermissionType.MANAGE_EVENTS_INVENTORY)
        ));
        assertFalse(permissions.checkPermission(MANAGER_ID, PermissionType.VIEW_PURCHASE_HISTORY));
    }

    @Test
    void GivenNewCompany_WhenCreated_ThenFounderIsOwner() {
        assertTrue(company.isOwner(FOUNDER_ID));
        assertEquals(FOUNDER_ID, company.getFounderId());
    }

    @Test
    void GivenActiveCompany_WhenDeactivated_ThenIsActiveFalse() {
        company.deactivate();
        assertFalse(company.isActive());
    }
}