package domain.company;

import domain.dataType.PermissionType;
import domain.dto.HierarchyDTO;

import java.util.*;

public class Permissions {
    private int founderId;//founder of the comapany
    private Set<Integer> ownerIds; // who are the owners of the company
    private HashMap<Integer, Hierarchy> companyTree; //the hash map with each manger and who assigned him and it's assignees
    private List<Integer> pandingOwners;

    public Permissions(int founderId) {
        this.founderId = founderId;
        this.ownerIds = new HashSet<>();
        ownerIds.add(founderId);
        companyTree = new HashMap<>();
        pandingOwners = new ArrayList<>();
    }

    /** Deep-copy constructor used by Company's copy constructor */
    public Permissions(Permissions other) {
        this.founderId = other.founderId;
        this.ownerIds = new HashSet<>(other.ownerIds);
        this.companyTree = new HashMap<>();
        for (Map.Entry<Integer, Hierarchy> entry : other.companyTree.entrySet()) {
            Hierarchy orig = entry.getValue();
            this.companyTree.put(entry.getKey(), new Hierarchy(
                    orig.getMyManager(),
                    new ArrayList<>(orig.getMyAppointees()),
                    new HashSet<>(orig.getAllPermissions())
            ));
        }
        this.pandingOwners = new ArrayList<>(other.pandingOwners);
    }
    public int getFounderId() {
        return founderId;
    }
    public void addOwner(int ownerId) {
        pandingOwners.add(ownerId);
    }

    public boolean isPendingOwner(int userId) {
        return pandingOwners.contains(userId);
    }

    public void OwnerAppointeeRespond(int ownerId, boolean appointee) {
        pandingOwners.remove(Integer.valueOf(ownerId));
        if(appointee) {
            ownerIds.add(ownerId);
            if(isManager(ownerId)){
                removeManagerFromTree(ownerId);
            }
        }
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
                HashMap<Integer, HierarchyDTO> result = new HashMap<>();
               for (Map.Entry<Integer, Hierarchy> entry : companyTree.entrySet()) {
                   Hierarchy h = entry.getValue();
                        result.put(entry.getKey(), new HierarchyDTO(
                                        h.getMyManager(), h.getMyAppointees(), h.getAllPermissions()));
                    }
                return result;
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

    public boolean isManager(int userId) {
        return companyTree.containsKey(userId);
    }

    /** Adds a manager to the tree and registers them as an appointee of their appointer. */
    public void addToTree(int managerId, int appointedBy, Set<PermissionType> permissions) {
        companyTree.put(managerId, new Hierarchy(appointedBy, new ArrayList<>(), new HashSet<>(permissions)));
        if (companyTree.containsKey(appointedBy)) {
            companyTree.get(appointedBy).addAppointee(managerId);
        }
    }

    /**
     * Removes a manager from the tree and cascades:
     * - removes them from their appointer's appointee list
     * - reassigns their direct sub-managers to the founder
     */
    public void removeManagerFromTree(int managerId) {
        Hierarchy removed = companyTree.remove(managerId);
        if (removed == null) return;
        Hierarchy appointer = companyTree.get(removed.getMyManager());
        if (appointer != null)
            appointer.getMyAppointees().remove(Integer.valueOf(managerId));
        for (int subId : removed.getMyAppointees()) {
            Hierarchy sub = companyTree.get(subId);
            if (sub != null) sub.setMyManager(founderId);
        }
    }

    /** Changes a manager's appointer — used when the original appointer is removed from the company. */
    public void changeAppointer(int managerId, int newAppointer) {
        Hierarchy h = companyTree.get(managerId);
        if (h != null) h.setMyManager(newAppointer);
    }

    public void updateManagerPermissions(int ownerId, int managerId, Set<PermissionType> newPermissions) {
        Hierarchy h = companyTree.get(managerId);
        if (h == null) {
            throw new IllegalArgumentException("Manager is not assigned to this company");
        }
        h.setPermissions(ownerId, newPermissions);
    }

}
