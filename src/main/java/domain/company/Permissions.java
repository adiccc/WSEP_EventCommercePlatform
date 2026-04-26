package domain.company;

import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;

import java.awt.event.HierarchyBoundsAdapter;
import java.util.*;

public class Permissions {
    private int founderId;//founder of the comapany
    private Set<Integer> ownerIds; // who are the owners of the company
    private HashMap<Integer, HierarchyDTO> companyTree; //the hash map with each manger and who assigned him and it's assignees

    public Permissions(int founderId) {
        this.founderId = founderId;
        this.ownerIds = new HashSet<>();
        addOwner(founderId);
        companyTree = new HashMap<>();
    }
    public int getFounderId() {
        return founderId;
    }
    public void addOwner(int ownerId) {
        ownerIds.add(ownerId);
    }

    public Set<Integer> getOwnerIds() {
        return ownerIds;
    }
    public boolean isOwner(int ownerId) {
        return ownerIds.contains(ownerId);
    }
    public boolean checkPermission(int userId, PermissionType type) {
        if (isOwner(userId)) return true; //owner has allPermissions in the company
        if (companyTree.containsKey(userId)) {
            return companyTree.get(userId).getAllPermissions().contains(type);
        }
        return false;
    }

    public HashMap<Integer, HierarchyDTO> getCompanyTree() {
        return companyTree;
    }
}
