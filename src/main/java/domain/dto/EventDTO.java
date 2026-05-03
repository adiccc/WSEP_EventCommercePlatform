package domain.dto;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.Event;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.Order;
import domain.policy.Discount;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EventDTO {
    private Integer eventID;
    private String eventName;
    private String eventDate;
    private String eventLocation; //get Georgraphical area
    private int creatorId;
    private String saleStartDate;
    private String categoryEvent;

    public EventDTO(Integer eventID, String eventName, LocalDateTime eventDate, LocalDateTime saleStartDate, CategoryEvent categoryEvent, GeographicalArea eventLocation, int creatorId) {
        this.eventID = eventID;
        this.eventName = eventName;
        this.eventDate = eventDate.toString();
        this.eventLocation = eventLocation.name();
        this.saleStartDate = saleStartDate.toString();
        this.categoryEvent = categoryEvent.name();
        this.creatorId = creatorId;
    }

    public EventDTO(Event event) {
        this.eventID = event.getId();
        this.eventName = event.getName();
        this.eventDate = event.getDate().toString();
        this.eventLocation = event.getLocation().name();
        this.saleStartDate = event.getSaleStartDate().toString();
        this.categoryEvent = event.getCategoryEvent().name();
        this.creatorId = event.getCreatorId();
    }

    public Integer getEventID() {
        return eventID;
    }

    public String getCategoryEvent() {
        return categoryEvent;
    }

    public String getLocation() {
        return eventLocation;
    }

    public String getName() {
        return eventName;
    }

}
