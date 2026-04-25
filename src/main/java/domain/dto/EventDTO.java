package domain.dto;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.event.EventMap;
import domain.event.EventQueue;
import domain.event.Order;
import domain.policy.Discount;
import domain.policy.Purcase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class EventDTO {
    private int eventID;
    private String eventName;
    private LocalDate eventDate;
    private String eventLocation; //get Georgraphical area
    private int creatorId;
    private LocalDateTime saleStartDate;
    private CategoryEvent categoryEvent;

    public EventDTO() {}

}
