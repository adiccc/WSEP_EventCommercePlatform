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
    protected boolean isActive = true;  // false when account is suspended/removed by admin
    private long version;

    public Member(String email, String password, String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth, String address) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.version = 0;
    }
    public Member(Member member) {
        super(member.getIdentifier());
        this.userId = member.getUserId();
        this.password = member.getPassword();
        this.firstName=member.firstName;
        this.lastName=member.lastName;
        this.phoneNumber=member.phoneNumber;
        this.dateOfBirth=member.dateOfBirth;
        this.address=member.address;
        this.version=member.version;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
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

    public boolean isActive() { return isActive; }
    public void deactivate() { this.isActive = false; }

}