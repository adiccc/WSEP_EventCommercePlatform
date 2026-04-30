package domain.user;

import domain.activeOrder.ActiveOrder;
import domain.dto.UserDTO;

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
        this.isActive=member.isActive;
        this.version=member.version;
        this.setConnected(member.isConnected());
        member.getRoles().forEach(this::addRole);
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Member other = (Member) obj;
        return userId==other.userId && version == other.getVersion();
    }

    public UserDTO getUserDTO() {
        return new UserDTO(this.getIdentifier(), this.firstName, this.lastName, this.password,
                this.dateOfBirth.getDayOfMonth(), this.dateOfBirth.getMonthValue(), this.dateOfBirth.getYear(),
                this.address, this.phoneNumber);
    }

}