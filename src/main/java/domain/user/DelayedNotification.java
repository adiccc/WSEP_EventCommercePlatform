package domain.user;

import DTO.NotifyPayload;
import DTO.NotifyType;
import jakarta.persistence.*;

@Entity
@Table(name = "delayed_notifications")
public class DelayedNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifyType type;
    @Embedded
    private NotifyPayload payload;

    protected DelayedNotification() {
        //for JPA
    }

    public DelayedNotification(NotifyType type, NotifyPayload payload) {
        this.type = type;
        this.payload = payload;
    }

    public NotifyType getType() {
        return type;
    }

    public NotifyPayload getPayload() {
        return payload;
    }
}

