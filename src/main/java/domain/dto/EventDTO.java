package domain.dto;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.Order;
import domain.policy.Discount;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EventDTO {
    private String eventID;
    private String eventName;
    private LocalDateTime eventDate;
    private GeographicalArea eventLocation; //get Georgraphical area
    private int creatorId;
    private LocalDateTime saleStartDate;
    private CategoryEvent categoryEvent;

    public EventDTO(String eventID, String eventName, LocalDateTime eventDate, LocalDateTime saleStartDate, CategoryEvent categoryEvent, GeographicalArea eventLocation, int creatorId) {
        this.eventID = eventID;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventLocation = eventLocation;
        this.saleStartDate = saleStartDate;
        this.categoryEvent = categoryEvent;
        this.creatorId = creatorId;
    }
}
