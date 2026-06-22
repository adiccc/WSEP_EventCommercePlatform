package DTO;

import domain.dataType.PermissionType;

import java.util.List;
import java.util.Set;

public class HierarchyDTO {
    private final int myManager;
    private final List<Integer> myAppointees;
    private final Set<PermissionType> allPermissions;

    public HierarchyDTO(int myManager, List<Integer> myAppointees, Set<PermissionType> allPermissions) {
        this.myManager = myManager;
        this.myAppointees = myAppointees;
        this.allPermissions = allPermissions;
    }

    public int getMyManager() { return myManager; }
    public List<Integer> getMyAppointees() { return myAppointees; }
    public Set<PermissionType> getAllPermissions() { return allPermissions; }
}
