package domain.event;

import domain.policy.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class Event {
    private String id;
    private int companyId;
    private int creatorId;
    private EventMap eventMap;
    private EventQueue eventQueue;
    private LocalDateTime date;
    private String name;
    private LocalDateTime saleStartDate;
    private boolean hasLottery;
    private Purcase purchasePolicy;
    private Discount discountPolicy;
    private boolean active;


    public Event(int companyId, int creatorId, LocalDateTime date, String name, LocalDateTime saleStartDate, boolean hasLottery) {
        this.eventMap = null;
        this.eventQueue = null;
        this.companyId=companyId;
        this.creatorId=creatorId;
        this.date = date;
        this.name = name;
        this.saleStartDate = saleStartDate;
        this.hasLottery = hasLottery;
        purchasePolicy = new PurchasePolicy();
        purchasePolicy.addRule(new MaxTicketsRule(20));
        discountPolicy = new DiscountPolicy();
        discountPolicy.addDiscount(new LimitedDiscount(0.1, 5));
        this.id = LocalDateTime.now().hashCode() + String.valueOf(creatorId);
        active = false;
    }

    public int getCompanyId() {
        return companyId;
    }
    public int getCreatorId(){
        return creatorId;
    }

    public void setMap(EventMap eventMap) {
        this.eventMap = eventMap;
    }

    public EventMap getMap() {
        return eventMap;
    }

    public boolean isAvailableForSale() {
        return saleStartDate.isBefore(LocalDateTime.now()) && eventMap != null;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getSaleStartDate() {
        return saleStartDate;
    }
    public boolean hasLottery() {
        return hasLottery;
    }

    public String getId() {
        return id;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    public void setDate(LocalDateTime date) {
        this.date = date;
    }


    public boolean isActive() {
        return active;
    }
}