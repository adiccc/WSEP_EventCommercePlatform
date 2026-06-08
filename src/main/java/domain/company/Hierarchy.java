package domain.company;

import domain.dataType.PermissionType;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "hierarchies")
public class Hierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hierarchy_id")
    private Long id;

    @Column(name = "my_manager", nullable = false)
    private int myManager;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hierarchy_appointees",
            joinColumns = @JoinColumn(name = "hierarchy_id")
    )
    @Column(name = "appointee_id")
    private List<Integer> myAppointees;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hierarchy_permissions",
            joinColumns = @JoinColumn(name = "hierarchy_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<PermissionType> allPermissions;

    /** Required by JPA — do not use directly */
    public Hierarchy() {
        this.myAppointees = new ArrayList<>();
        this.allPermissions = new HashSet<>();
    }

    public Hierarchy(int myManager, List<Integer> myAppointees, Set<PermissionType> allPermissions) {
        this.myManager = myManager;
        this.myAppointees = (myAppointees != null) ? myAppointees : new ArrayList<>();
        this.allPermissions = (allPermissions != null) ? allPermissions : new HashSet<>();
    }

    public Long getId() { return id; }

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
