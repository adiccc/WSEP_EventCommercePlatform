package domain.user;

import java.util.ArrayList;
import java.util.List;

public class User {

    private String userId;
    private boolean connected;
    private List<Role> roles;

    public User(String userId) {
        this.userId = userId;
        this.connected = false;
        this.roles = new ArrayList<>();
    }

    protected User() {
        this.roles = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public List<Role> getRoles() {
        return roles;
    }
}
