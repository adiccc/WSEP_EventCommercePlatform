package DTO;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Table(name = "delayed_notifications")
public class NotifyDTO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotifyType type;
    @Embedded
    private NotifyPayload payload;

    protected NotifyDTO() {
        //for JPA
    }

    public NotifyDTO(NotifyType type, NotifyPayload payload) {
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