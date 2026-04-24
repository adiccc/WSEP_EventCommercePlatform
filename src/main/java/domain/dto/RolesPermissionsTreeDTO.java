package domain.dto;

import domain.dataType.PermissionType;

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
public class RolesPermissionTypeTreeDTO {

    private final int founderId;
    private final Set<Integer> ownerIds;
    private final Map<String, Set<PermissionType>> managersPermissionType;

    public RolesPermissionTypeTreeDTO(int founderId,
                                   Set<Integer> ownerIds,
                                   Map<String, Set<PermissionType>> managersPermissionType) {
        this.founderId = founderId;
        this.ownerIds = ownerIds;
        this.managersPermissionType = managersPermissionType;
    }

    public int getFounderId() {
        return founderId;
    }

    public Set<Integer> getOwnerIds() {
        return ownerIds;
    }

    public Map<String, Set<PermissionType>> getManagersPermissionType() {
        return managersPermissionType;
    }

    @Override
    public String toString() {
        return "RolesPermissionTypeTreeDTO{" +
                "founderId=" + founderId +
                ", ownerIds=" + ownerIds +
                ", managersPermissionType=" + managersPermissionType +
                '}';
    }
}
