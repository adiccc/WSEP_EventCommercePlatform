package DTO;
import jakarta.persistence.Embeddable;
import io.swagger.v3.oas.models.security.SecurityScheme;
@Embeddable
public class NotifyPayload {
    private String message;
    private Integer eventId;
    private Integer companyId;

    public NotifyPayload() {
        // for JPA
    }
    public NotifyPayload(String message, Integer eventId, Integer companyId) {
        this.message = message;
        this.eventId = eventId;
        this.companyId = companyId;
    }

    public NotifyPayload(String message) {
        this.message = message;
        this.eventId = null;
        this.companyId = null;
    }

    public String getMessage() {
        return message;
    }

    public Integer getEventId() {
        return eventId;
    }

    public Integer getCompanyId() {
        return companyId;
    }

}
