package domain.user;

public class Member extends User {

    private String password;
    private String email;

    public Member(String email, String password) {
        super(email); // use email as userId for member identity
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }
}
