package DTO;

import domain.event.Event;

public class EventDetailsDTO {
    private Integer eventID;
    private String eventName;
    private String eventDate;
    private int companyId;
    private int creatorId;
    private String saleStartDate;
    private String categoryEvent;
    private String eventLocation;
    private boolean hasLottery;
    private EventMapDTO eventMap;
    private String purchasePolicy;
    private String discountPolicy;

    public EventDetailsDTO(Event event) {
        this.eventID = event.getId();
        this.eventName = event.getName();
        this.eventDate = event.getDate().toString();
        this.eventLocation = event.getLocation().name();
        this.hasLottery = event.hasLottery();
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

    public Integer getEventID() {
        return eventID;
    }

    public Integer getId() {
        return eventID;
    }

    public EventMapDTO getMap() {
        return eventMap;
    }

    public String getDate() {
        return eventDate;
    }

    public String getName() {
        return eventName;
    }

    public String getCategoryEvent() {
        return categoryEvent;
    }

    public String getLocation() {
        return eventLocation;
    }

    public String getSaleStartDate() {
        return saleStartDate;
    }

    public int getCompanyId() {
        return companyId;
    }

    public String getPurchasePolicy() {
        return purchasePolicy;
    }

    public String getDiscountPolicy() {
        return discountPolicy;
    }

    public boolean hasLottery() {
        return hasLottery;
    }
}
