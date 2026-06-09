package domain.user;

import DTO.NotifyDTO;
import domain.activeOrder.ActiveOrder;
import domain.dto.UserDTO;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "members")
public class Member extends User{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    protected Integer userId;
    protected String password; //encrypted password
    protected String firstName;
    protected String lastName;
    protected String phoneNumber;
    protected LocalDate dateOfBirth;
    protected String address;
    @Enumerated(EnumType.STRING)
    protected ActivationStatus activationStatus;
    @Version
    private long version;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private List<NotifyDTO> delayedNotifications = new ArrayList<>();

    protected Member() {
        super();
    }

    public Member(String email, String password, String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth, String address,ActivationStatus activationStatus) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.version = 0;
        this.activationStatus=activationStatus;
        this.delayedNotifications = new ArrayList<>();
    }
    public Member(String email, String password, String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth, String address, List<NotifyDTO> delayedNotifications) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.version = 0;
        this.activationStatus=ActivationStatus.ACTIVE;
        this.delayedNotifications =delayedNotifications != null ? delayedNotifications : new ArrayList<>();
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
        this.activationStatus=member.activationStatus;
        this.delayedNotifications = member.getDelayedNotifications();

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
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }

    public boolean isActive() { return activationStatus == ActivationStatus.ACTIVE; }
    public void deactivate() { this.activationStatus=ActivationStatus.INACTIVE; }
    public boolean isSuspended() { return activationStatus == ActivationStatus.SUSPENDED; }
    public void suspend(){
        activationStatus=ActivationStatus.SUSPENDED;
    }
    public void unsuspend(){
        activationStatus=ActivationStatus.ACTIVE;
    }
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
    public void addDelayedNotification(NotifyDTO notification) {
        this.delayedNotifications.add(notification);
    }

    public List<NotifyDTO> getDelayedNotifications() {
        return this.delayedNotifications;
    }

    public void clearDelayedNotifications() {
        this.delayedNotifications.clear();
    }
}