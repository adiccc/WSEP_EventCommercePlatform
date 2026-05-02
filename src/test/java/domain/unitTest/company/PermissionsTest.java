package domain.unitTest.company;

import domain.company.Permissions;
import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {

    private static final int FOUNDER_ID     = 1;
    private static final int OWNER_ID       = 2;
    private static final int MANAGER_ID     = 3;
    private static final int SUB_MANAGER_ID = 4;
    private static final int STRANGER_ID    = 99;

    private Permissions permissions;

    @BeforeEach
    void setUp() {
        permissions = new Permissions(FOUNDER_ID);
    }

    // ===================== addOwner / removeOwner / isOwner =====================

    @Test
    void GivenAddedOwner_WhenIsOwnerCalled_ThenTrue() {
        permissions.addOwner(OWNER_ID);
        permissions.OwnerAppointeeRespond(OWNER_ID, true);
        assertTrue(permissions.isOwner(OWNER_ID));
    }

    @Test
    void GivenRemovedOwner_WhenIsOwnerCalled_ThenFalse() {
        permissions.addOwner(OWNER_ID);
        permissions.removeOwner(OWNER_ID);
        assertFalse(permissions.isOwner(OWNER_ID));
    }

    @Test
    void GivenStranger_WhenIsOwnerCalled_ThenFalse() {
        assertFalse(permissions.isOwner(STRANGER_ID));
    }

    // ===================== isManager =====================

    @Test
    void GivenUserNotInTree_WhenIsManagerCalled_ThenFalse() {
        assertFalse(permissions.isManager(STRANGER_ID));
    }

    @Test
    void GivenUserAddedToTree_WhenIsManagerCalled_ThenTrue() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        assertTrue(permissions.isManager(MANAGER_ID));
    }

    @Test
    void GivenOwner_WhenIsManagerCalled_ThenFalse() {
        assertFalse(permissions.isManager(FOUNDER_ID));
    }

    // ===================== checkPermission =====================

    @Test
    void GivenOwner_WhenCheckPermission_ThenTrueForAllPermissions() {
        for (PermissionType type : PermissionType.values()) {
            assertTrue(permissions.checkPermission(FOUNDER_ID, type));
        }
    }

    @Test
    void GivenUnknownUser_WhenCheckPermission_ThenFalse() {
        assertFalse(permissions.checkPermission(STRANGER_ID, PermissionType.CREATE_EVENT));
    }

    // ===================== getCompanyTree =====================

    @Test
    void GivenManagerInTree_WhenGetCompanyTree_ThenDTOReflectsStoredData() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        HierarchyDTO dto = permissions.getCompanyTree().get(MANAGER_ID);

        assertNotNull(dto);
        assertEquals(FOUNDER_ID, dto.getMyManager());
        assertTrue(dto.getAllPermissions().contains(PermissionType.CREATE_EVENT));
    }

    @Test
    void GivenReturnedSnapshot_WhenNewEntryAdded_ThenInternalStateUnchanged() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());

        HashMap<Integer, HierarchyDTO> snapshot = permissions.getCompanyTree();
        snapshot.put(STRANGER_ID, new HierarchyDTO(FOUNDER_ID, null, null));

        assertFalse(permissions.isManager(STRANGER_ID));
    }

    // ===================== addToTree =====================

    @Test
    void GivenManagerAddedUnderOwner_WhenGetCompanyTree_ThenPermissionsStored() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, EnumSet.of(PermissionType.MANAGE_POLICIES));

        assertEquals(EnumSet.of(PermissionType.MANAGE_POLICIES),
                permissions.getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }

    @Test
    void GivenSubManagerAddedUnderManager_WhenGetCompanyTree_ThenManagerHasAppointee() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        permissions.addToTree(SUB_MANAGER_ID, MANAGER_ID, new HashSet<>());

        assertTrue(permissions.getCompanyTree().get(MANAGER_ID).getMyAppointees().contains(SUB_MANAGER_ID));
    }

    @Test
    void GivenManagerAddedUnderOwnerNotInTree_WhenAddToTree_ThenManagerStoredWithoutCrash() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());

        assertTrue(permissions.isManager(MANAGER_ID));
        assertFalse(permissions.isManager(FOUNDER_ID));
    }

    // ===================== removeManagerFromTree =====================

    @Test
    void GivenManagerInTree_WhenRemoved_ThenIsManagerFalse() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        permissions.removeManagerFromTree(MANAGER_ID);

        assertFalse(permissions.isManager(MANAGER_ID));
    }

    @Test
    void GivenManagerWithSubManager_WhenRemoved_ThenSubManagerReassignedToFounder() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        permissions.addToTree(SUB_MANAGER_ID, MANAGER_ID, new HashSet<>());

        permissions.removeManagerFromTree(MANAGER_ID);

        assertEquals(FOUNDER_ID, permissions.getCompanyTree().get(SUB_MANAGER_ID).getMyManager());
    }

    @Test
    void GivenManagerInTree_WhenRemoved_ThenRemovedFromAppointerAppointeeList() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        permissions.addToTree(SUB_MANAGER_ID, MANAGER_ID, new HashSet<>());

        permissions.removeManagerFromTree(SUB_MANAGER_ID);

        assertFalse(permissions.getCompanyTree().get(MANAGER_ID).getMyAppointees().contains(SUB_MANAGER_ID));
    }

    @Test
    void GivenNonExistentManager_WhenRemoveManagerFromTree_ThenNoException() {
        assertDoesNotThrow(() -> permissions.removeManagerFromTree(STRANGER_ID));
    }

    // ===================== changeAppointer =====================

    @Test
    void GivenManagerInTree_WhenChangeAppointer_ThenAppointerUpdated() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());

        permissions.changeAppointer(MANAGER_ID, OWNER_ID);

        assertEquals(OWNER_ID, permissions.getCompanyTree().get(MANAGER_ID).getMyManager());
    }

    @Test
    void GivenNonExistentManager_WhenChangeAppointer_ThenNoException() {
        assertDoesNotThrow(() -> permissions.changeAppointer(STRANGER_ID, FOUNDER_ID));
    }

    // ===================== getSubTreeAppointees =====================

    @Test
    void GivenManagerWithNoAppointees_WhenGetSubTree_ThenReturnsOnlySelf() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());

        Set<Integer> subtree = permissions.getSubTreeAppointees(MANAGER_ID);

        assertEquals(Set.of(MANAGER_ID), subtree);
    }

    @Test
    void GivenManagerWithSubManager_WhenGetSubTree_ThenReturnsBothNodes() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());
        permissions.addToTree(SUB_MANAGER_ID, MANAGER_ID, new HashSet<>());

        Set<Integer> subtree = permissions.getSubTreeAppointees(MANAGER_ID);

        assertTrue(subtree.contains(MANAGER_ID));
        assertTrue(subtree.contains(SUB_MANAGER_ID));
        assertEquals(2, subtree.size());
    }

    // ===================== updateManagerPermissions =====================

    @Test
    void GivenValidOwnerAndManager_WhenUpdatePermissions_ThenPermissionsReplaced() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, EnumSet.of(PermissionType.CREATE_EVENT));

        permissions.updateManagerPermissions(FOUNDER_ID, MANAGER_ID, EnumSet.of(PermissionType.DELETE_EVENT));

        assertEquals(EnumSet.of(PermissionType.DELETE_EVENT),
                permissions.getCompanyTree().get(MANAGER_ID).getAllPermissions());
    }

    @Test
    void GivenValidUpdate_WhenUpdateWithEmptySet_ThenAllPermissionsCleared() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, EnumSet.of(PermissionType.CREATE_EVENT, PermissionType.DELETE_EVENT));

        permissions.updateManagerPermissions(FOUNDER_ID, MANAGER_ID, new HashSet<>());

        assertTrue(permissions.getCompanyTree().get(MANAGER_ID).getAllPermissions().isEmpty());
    }

    @Test
    void GivenManagerNotInTree_WhenUpdatePermissions_ThenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                permissions.updateManagerPermissions(FOUNDER_ID, STRANGER_ID, EnumSet.of(PermissionType.CREATE_EVENT)));
    }

    @Test
    void GivenWrongOwner_WhenUpdatePermissions_ThenSecurityException() {
        permissions.addToTree(MANAGER_ID, FOUNDER_ID, new HashSet<>());

        assertThrows(SecurityException.class, () ->
                permissions.updateManagerPermissions(OWNER_ID, MANAGER_ID, EnumSet.of(PermissionType.CREATE_EVENT)));
    }
}
