package domain.company;

import domain.dataType.PermissionType;

import java.util.EnumSet;
import java.util.Set;

public class ManagerAppointment {

    private int managerId;
    private Set<PermissionType> permissions;

    public ManagerAppointment(int managerId) {
        this.managerId = managerId;
        this.permissions = EnumSet.noneOf(PermissionType.class);
    }

    public ManagerAppointment(int managerId, Set<PermissionType> permissions) {
        this.managerId = managerId;
        this.permissions = permissions;
    }

    public int getManagerId() { return managerId; }

    public Set<PermissionType> getPermissions() { return permissions; }

    public void addPermission(PermissionType permission) {
        permissions.add(permission);
    }

    public void removePermission(PermissionType permission) {
        permissions.remove(permission);
    }

    public boolean hasPermission(PermissionType permission) {
        return permissions.contains(permission);
    }
}
