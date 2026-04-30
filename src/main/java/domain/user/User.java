package domain.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User {

    private String identifier;
    private List<Role> roles;
    private long version;

    public User(String identifier) {
        this.identifier = identifier;
        this.roles = new ArrayList<>();
        this.version = 0;
    }
    public User(User other) {
        this.identifier = other.getIdentifier();
        this.version = other.getVersion();
        this.roles = new ArrayList<>(other.getRoles());
    }

    protected User() {
        this.roles = new ArrayList<>();
    }

    public String getIdentifier() {
        return identifier;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public List<Role> getRoles() {
        return roles;
    }

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof User other) {
            return Objects.equals(this.identifier, other.identifier);
        }
        return false;
    }
}
