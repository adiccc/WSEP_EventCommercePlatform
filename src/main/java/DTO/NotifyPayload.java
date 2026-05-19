package DTO;

public class NotifyPayload {
    private String message;
    private Integer eventId;
    //TODO: ADD MORE FIELDS IF NEEDED

    public NotifyPayload(String message, Integer eventId) {
        this.message = message;
        this.eventId = eventId;
    }
    public NotifyPayload(String message) {
        this.message = message;
        this.eventId = null;
    }

    public String getMessage() {
        return message;
    }

    public Integer getEventId() {
        return eventId;
    }

}
