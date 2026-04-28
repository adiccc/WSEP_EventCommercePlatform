package domain.dto;

import domain.dataType.PermissionType;

import java.util.*;

public class HierarchyDTO {
    private int myManager; // who is my manager
    private List<Integer> myAppointees; //a list of the managers that under me
    private Set<PermissionType> allPermissions; //list of permissions of manager
    public HierarchyDTO(int myManager, List<Integer> myAppointees, Set<PermissionType> allPermissions) {
        this.myManager = myManager;
        this.myAppointees = (myAppointees != null) ? myAppointees : new ArrayList<>();
        this.allPermissions = (allPermissions != null) ? allPermissions : new HashSet<>();
    }

    public int getMyManager() {
        return myManager;
    }
    public void setMyManager(int myManager) {
        this.myManager = myManager;
    }
    public List<Integer> getMyAppointees() {
        return myAppointees;
    }
    public Set<PermissionType> getAllPermissions() {
        return allPermissions;
    }
    public void addAppointee(int appointee) {
        myAppointees.add(appointee);
    }
    public void addPermission(PermissionType permission) {
        this.allPermissions.add(permission);
    }

}
