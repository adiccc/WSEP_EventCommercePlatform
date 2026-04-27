package domain.company;

import domain.dataType.PermissionType;

import java.util.EnumSet;
import java.util.Set;

public class ManagerAppointment {

    private int managerId;
    private int appointedBy;   // the owner who appointed this manager
    private Set<PermissionType> permissions;

    public ManagerAppointment(int managerId, int appointedBy) {
        this.managerId = managerId;
        this.appointedBy = appointedBy;
        this.permissions = EnumSet.noneOf(PermissionType.class);
    }

    public ManagerAppointment(int managerId, int appointedBy, Set<PermissionType> permissions) {
        this.managerId = managerId;
        this.appointedBy = appointedBy;
        this.permissions = permissions;
    }
    public ManagerAppointment(ManagerAppointment managerAppointment) {
        this.managerId = managerAppointment.getManagerId();
        this.permissions = EnumSet.copyOf(managerAppointment.getPermissions());
    }

    public int getManagerId()    { return managerId; }
    public int getAppointedBy()  { return appointedBy; }
    public void setAppointedBy(int appointedBy) { this.appointedBy = appointedBy; }

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
