package domain.dto;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.Event;
import domain.event.EventMap;
import domain.policy.DiscountPolicy;
import domain.policy.PurchasePolicy;

import java.time.LocalDateTime;

public class EventDetailsDTO {
    private String eventID;
    private String eventName;
    private String eventDate;
    private int companyId;
    private int creatorId;
    private String saleStartDate;
    private String categoryEvent;
    private String eventLocation;
    private EventMapDTO eventMap;
    private String purchasePolicy;
    private String discountPolicy;

    public EventDetailsDTO(Event event) {
        this.eventID = event.getId();
        this.eventName = event.getName();
        this.eventDate = event.getDate().toString();
        this.eventLocation = event.getLocation().name();
        this.saleStartDate = event.getSaleStartDate().toString();
        this.categoryEvent = event.getCategoryEvent().name();
        this.creatorId = event.getCreatorId();
        this.companyId = event.getCompanyId();
        this.purchasePolicy = event.getPurchasePolicy().describe();
        this.discountPolicy = event.getDiscountPolicy().describe();
        if (event.getEventMap() != null) {
            this.eventMap = new EventMapDTO(event.getEventMap());
        } else {
            this.eventMap = null;
        }
    }

    public String getEventID() {
        return eventID;
    }

    public String getId() {
        return eventID;
    }

    public EventMapDTO getMap() {
        return eventMap;
    }

    public String getDate() {
        return eventDate;
    }
}
