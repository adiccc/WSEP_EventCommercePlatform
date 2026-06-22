package DTO;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public class NotifyPayload {
    @Column(nullable = false)
    private String message;

    @Column(name = "event_id")
    private Integer eventId;

    @Column(name = "company_id")
    private Integer companyId;

    // Discriminator for the notification's action target (e.g. "OWNER", "MANAGER")
    // so the UI/domain doesn't have to parse the message text to know which role an
    // appointment invite is for.
    @Column(name = "action_data")
    private String actionData;

    public NotifyPayload() {
        // for JPA
    }

    public NotifyPayload(String message) {
        this.message = message;
    }

    public NotifyPayload(String message, Integer eventId, Integer companyId) {
        this(message, eventId, companyId, null);
    }

    public NotifyPayload(String message, Integer eventId, Integer companyId, String actionData) {
        this.message = message;
        this.eventId = eventId;
        this.companyId = companyId;
        this.actionData = actionData;
    }

    public String getMessage()   { return message; }
    public Integer getEventId()  { return eventId; }
    public Integer getCompanyId(){ return companyId; }
    public String getActionData(){ return actionData; }
    public void setActionData(String actionData) { this.actionData = actionData; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotifyPayload)) return false;
        NotifyPayload that = (NotifyPayload) o;
        return Objects.equals(message, that.message)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(companyId, that.companyId)
                && Objects.equals(actionData, that.actionData);
    }
}
