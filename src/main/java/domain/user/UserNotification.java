package domain.user;

import DTO.NotifyPayload;
import DTO.NotifyType;
import jakarta.persistence.*;

@Entity
@Table(name = "user_notifications")
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifyType type;
    @Embedded
    private NotifyPayload payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    protected UserNotification() {
        //for JPA
    }

    public UserNotification(NotifyType type, NotifyPayload payload) {
        this.type = type;
        this.payload = payload;
        this.status = NotificationStatus.PENDING;
    }

    public NotifyType getType() {
        return type;
    }

    public NotifyPayload getPayload() {
        return payload;
    }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public Long getNotificationId() { return notificationId; }
}

