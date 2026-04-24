package domain.dto;

import domain.company.Permissions;

import java.util.Map;
import java.util.Set;

/**
 * DTO representing the roles and permissions tree of a production company.
 *
 * Tree structure:
 *   Founder
 *     └── Owners (one or more)
 *           └── Managers (each with their own permissions set)
 */
public class RolesPermissionsTreeDTO {

    private final int founderId;
    private final Set<Integer> ownerIds;
    private final Map<String, Set<Permissions>> managersPermissions;

    public RolesPermissionsTreeDTO(int founderId,
                                   Set<Integer> ownerIds,
                                   Map<String, Set<Permissions>> managersPermissions) {
        this.founderId = founderId;
        this.ownerIds = ownerIds;
        this.managersPermissions = managersPermissions;
    }

    public int getFounderId() {
        return founderId;
    }

    public Set<Integer> getOwnerIds() {
        return ownerIds;
    }

    public Map<String, Set<Permissions>> getManagersPermissions() {
        return managersPermissions;
    }

    @Override
    public String toString() {
        return "RolesPermissionsTreeDTO{" +
                "founderId=" + founderId +
                ", ownerIds=" + ownerIds +
                ", managersPermissions=" + managersPermissions +
                '}';
    }
}
