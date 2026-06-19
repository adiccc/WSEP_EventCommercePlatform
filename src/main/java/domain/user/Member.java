package domain.user;

import DTO.NotifyDTO;
import DTO.NotifyType;
import domain.dto.UserDTO;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "members")
public class Member extends User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    protected Integer userId;
    protected String password; // encrypted password
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
    private List<UserNotification> userNotifications = new ArrayList<>();

    protected Member() {
        super();
    }

    public Member(String email, String password, String firstName, String lastName, String phoneNumber,
            LocalDate dateOfBirth, String address, ActivationStatus activationStatus) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.version = 0;
        this.activationStatus = activationStatus;
        this.userNotifications = new ArrayList<>();
    }

    public Member(String email, String password, String firstName, String lastName, String phoneNumber,
            LocalDate dateOfBirth, String address, List<UserNotification> userNotifications) {
        super(email);
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
        this.address = address;
        this.version = 0;
        this.activationStatus = ActivationStatus.ACTIVE;
        this.userNotifications = userNotifications != null ? userNotifications : new ArrayList<>();
    }

    public Member(Member member) {
        super(member);
        this.userId = member.getUserId();
        this.password = member.getPassword();
        this.firstName = member.firstName;
        this.lastName = member.lastName;
        this.phoneNumber = member.phoneNumber;
        this.dateOfBirth = member.dateOfBirth;
        this.address = member.address;
        this.version = member.version;
        this.activationStatus = member.activationStatus;
        this.userNotifications = member.getPendingNotifications() != null
                ? new ArrayList<>(member.getPendingNotifications())
                : new ArrayList<>();
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isActive() {
        return activationStatus == ActivationStatus.ACTIVE;
    }

    public void deactivate() {
        this.activationStatus = ActivationStatus.INACTIVE;
    }

    public boolean isSuspended() {
        return activationStatus == ActivationStatus.SUSPENDED;
    }

    public void suspend() {
        activationStatus = ActivationStatus.SUSPENDED;
    }

    public void unsuspend() {
        activationStatus = ActivationStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
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

    public void addPendingNotification(UserNotification notification) {
        this.userNotifications.add(notification);
    }

    public List<UserNotification> getPendingNotifications() {
        return this.userNotifications;
    }

    public void clearPendingNotifications() {
        this.userNotifications.clear();
    }

    public List<NotifyDTO> fetchAndMarkPendingNotifications() {
        List<NotifyDTO> pendingNotifications = new ArrayList<>();

        for (UserNotification notification : this.userNotifications) {
            if (notification.getStatus() == NotificationStatus.PENDING) {
                pendingNotifications.add(new NotifyDTO(notification.getType(), notification.getPayload()));
                // ROLE_APPOINTMENT_REQUEST requires explicit accept/reject — keep PENDING
                // so it remains visible in the notification list until the user responds.
                if (notification.getType() != NotifyType.ROLE_APPOINTMENT_REQUEST) {
                    notification.setStatus(NotificationStatus.DELIVERED);
                }
            }
        }
        return pendingNotifications;
    }

    public void setMessageStatus(Long id, NotificationStatus status) {
        for (UserNotification noti : this.userNotifications) {
            if (noti.getNotificationId().equals(id)) {
                noti.setStatus(status);
                return;
            }
        }
    }

    public Long getMessageId(UserNotification userNotification) {
        for (UserNotification un : this.userNotifications) {
            if (un.getType().equals(userNotification.getType())
                    && un.getPayload().equals(userNotification.getPayload())
                    && un.getStatus().equals(userNotification.getStatus()))
                return un.getNotificationId();
        }
        return null;
    }

    // Marks ONE PENDING ROLE_APPOINTMENT_REQUEST notification matching companyId + role keyword
    // (e.g. "owner" or "manager") as delivered. Used on REJECT so only the rejected invite is
    // cleared and the other role's invite stays visible.
    public void markAppointmentRequestDelivered(int companyId, String roleKeyword) {
        for (UserNotification un : this.userNotifications) {
            if (un.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST
                    && un.getStatus() == NotificationStatus.PENDING
                    && un.getPayload() != null
                    && Integer.valueOf(companyId).equals(un.getPayload().getCompanyId())
                    && un.getPayload().getMessage() != null
                    && un.getPayload().getMessage().contains(roleKeyword)) {
                un.setStatus(NotificationStatus.DELIVERED);
                return;
            }
        }
    }

    // Marks ALL PENDING ROLE_APPOINTMENT_REQUEST notifications for a given company as delivered.
    // Used on ACCEPT — a user can only hold one role per company, so accepting either owner or
    // manager invalidates any other pending invite for the same company.
    public void markAllAppointmentRequestsDelivered(int companyId) {
        for (UserNotification un : this.userNotifications) {
            if (un.getType() == NotifyType.ROLE_APPOINTMENT_REQUEST
                    && un.getStatus() == NotificationStatus.PENDING
                    && un.getPayload() != null
                    && Integer.valueOf(companyId).equals(un.getPayload().getCompanyId())) {
                un.setStatus(NotificationStatus.DELIVERED);
            }
        }
    }
}