package domain.user;

import java.time.LocalDate;
import java.util.Date;

public class Member extends User{
    protected Integer userId;
    protected String password; //encrypted password
    protected String firstName;
    protected String lastName;
    protected String phoneNumber;
    protected LocalDate dateOfBirth;
    protected String address;

    public Member(String email, String password, String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth, String address) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
    }
    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
    public String getPassword() {
        return password;
    }

}