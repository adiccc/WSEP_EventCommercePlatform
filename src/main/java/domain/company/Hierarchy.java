package domain.company;

import domain.dataType.PermissionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Hierarchy {
    private int myManager;
    private List<Integer> myAppointees;
    private Set<PermissionType> allPermissions;

    public Hierarchy(int myManager, List<Integer> myAppointees, Set<PermissionType> allPermissions) {
        this.myManager = myManager;
        this.myAppointees = (myAppointees != null) ? myAppointees : new ArrayList<>();
        this.allPermissions = (allPermissions != null) ? allPermissions : new HashSet<>();
    }

    public int getMyManager() { return myManager; }
    public void setMyManager(int myManager) { this.myManager = myManager; }

    public List<Integer> getMyAppointees() { return myAppointees; }

    public Set<PermissionType> getAllPermissions() { return allPermissions; }

    public void addAppointee(int appointee) { myAppointees.add(appointee); }

    public void addPermission(PermissionType permission) { allPermissions.add(permission); }

    public void setPermissions(int ownerId, Set<PermissionType> permissions) {
        if (myManager != ownerId) {
            throw new SecurityException("Owner is not authorized to modify this manager's permissions");
        }
        this.allPermissions = new HashSet<>(permissions);
    }
}
