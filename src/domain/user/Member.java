package domain.user;

public class Member extends User{
    private String password;
    private String email;

    public Member(String email, String password) {
        this.email = email;
        this.password = password;
    }

}