package domain.user;

import domain.activeOrder.ActiveOrder;
import domain.dto.UserDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

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
        super(member);
        this.userId = member.getUserId();
        this.password = member.getPassword();
        this.firstName=member.firstName;
        this.lastName=member.lastName;
        this.phoneNumber=member.phoneNumber;
        this.dateOfBirth=member.dateOfBirth;
        this.address=member.address;
        this.version=member.version;
        this.isActive = member.isActive;
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
        if (obj instanceof Member other) {
            return Objects.equals(this.userId, other.userId) &&
                    Objects.equals(this.getIdentifier(), other.getIdentifier());
        }
        return false;
    }

    public UserDTO getUserDTO() {
        return new UserDTO(this.getIdentifier(), this.firstName, this.lastName, this.password,
                this.dateOfBirth.getDayOfMonth(), this.dateOfBirth.getMonthValue(), this.dateOfBirth.getYear(),
                this.address, this.phoneNumber);
    }

}