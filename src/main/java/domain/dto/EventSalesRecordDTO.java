package domain.dto;

public class EventSalesRecordDTO {
    private String eventId;
    private String eventName;
    private int creatorId;
    private int numTicketsSold;
    private double revenue; //the income of each order done to that event

    public EventSalesRecordDTO(String eventId, String eventName, int creatorId, int ticketsSold, double revenue) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.creatorId = creatorId;
        this.numTicketsSold = ticketsSold;
        this.revenue = revenue;
    }

    public String getEventId() { return eventId; }
    public String getEventName() { return eventName; }
    public int getCreatorId() { return creatorId; }
    public int getNumTicketsSold() { return numTicketsSold; }
    public double getRevenue() { return revenue; }
}