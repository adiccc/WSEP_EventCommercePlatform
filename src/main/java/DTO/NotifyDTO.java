package DTO;
import domain.user.UserNotification;

public class NotifyDTO {
    private NotifyType type;
    private NotifyPayload payload;

    public NotifyDTO(NotifyType type, NotifyPayload payload) {
        this.type = type;
        this.payload = payload;
    }

    public NotifyDTO(UserNotification notification) {
        this.type = notification.getType();
        this.payload = notification.getPayload();
    }

    public NotifyType getType() {
        return type;
    }

    public NotifyPayload getPayload() {
        return payload;
    }
}