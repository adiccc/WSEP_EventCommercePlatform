package domain.company;

import java.util.EnumSet;
import java.util.Set;

public class ManagerAppointment {

    private String managerId;
    private Set<Permissions> permissions;

    public ManagerAppointment(String managerId) {
        this.managerId = managerId;
        this.permissions = EnumSet.noneOf(Permissions.class);
    }

    public ManagerAppointment(String managerId, Set<Permissions> permissions) {
        this.managerId = managerId;
        this.permissions = permissions;
    }

    public String getManagerId() { return managerId; }

    public Set<Permissions> getPermissions() { return permissions; }

    public void addPermission(Permissions permission) {
        permissions.add(permission);
    }

    public void removePermission(Permissions permission) {
        permissions.remove(permission);
    }

    public boolean hasPermission(Permissions permission) {
        return permissions.contains(permission);
    }
}
