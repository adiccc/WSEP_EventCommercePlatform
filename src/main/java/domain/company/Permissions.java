package domain.company;

import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;

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

    /** Deep-copy constructor used by Company's copy constructor */
    public Permissions(Permissions other) {
        this.founderId = other.founderId;
        this.ownerIds = new HashSet<>(other.ownerIds);
        this.companyTree = new HashMap<>();
        for (Map.Entry<Integer, HierarchyDTO> entry : other.companyTree.entrySet()) {
            HierarchyDTO orig = entry.getValue();
            this.companyTree.put(entry.getKey(), new HierarchyDTO(
                    orig.getMyManager(),
                    new ArrayList<>(orig.getMyAppointees()),
                    new HashSet<>(orig.getAllPermissions())
            ));
        }
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
        public Set<Integer> getSubTreeAppointees(int rootUserId) { //function that gets all the subtree of some user
        Set<Integer> subTreeAppointees = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(rootUserId);
        subTreeAppointees.add(rootUserId);
        while (!queue.isEmpty()) {
            int currentUserId = queue.poll();
            if(companyTree.containsKey(currentUserId)) {
                List<Integer> directAppointees = companyTree.get(currentUserId).getMyAppointees();
                for (Integer directAppointee : directAppointees) {
                    if(!subTreeAppointees.contains(directAppointee)) {
                        subTreeAppointees.add(directAppointee);
                        queue.add(directAppointee);
                    }
                }
            }
        }
        return subTreeAppointees;
    }
        public void removeOwner(int ownerId) {
        ownerIds.remove(ownerId);
    }

}
