package domain.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class User {

    private String identifier;
    private State state;

    public User(String identifier) {
        this.identifier = identifier;
        this.state=new State();
    }
    public User(User other) {
        this.identifier = other.getIdentifier();
        this.state = other.state;
    }

    protected User() {
        this.state = new State();
    }

    public String getIdentifier() {
        return identifier;
    }

    public void changeState(State state) {
        this.state.changeState(state);
    }

//    public void removeManagerRole(int companyId) {
//        roles.removeIf(r -> r instanceof Manager && ((Manager) r).getCompanyId() == companyId);
//    }

    public State getRole() {
        return state;
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
