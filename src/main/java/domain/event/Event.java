package domain.event;

import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import domain.policy.*;

import java.time.LocalDateTime;

import java.util.ArrayList;
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
    private Purchase purchasePolicy;
    private DiscountPolicy discountPolicy;
    private boolean active;
    private GeographicalArea location;
    private CategoryEvent categoryEvent;
    private List<Order> orders;


    public Event(int companyId, int creatorId, LocalDateTime date, String name, LocalDateTime saleStartDate, boolean hasLottery, GeographicalArea location, CategoryEvent categoryEvent) {
        this.eventMap = null;
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
        this.location = location;
        this.categoryEvent = categoryEvent;
        this.eventQueue = new EventQueue();
        this.orders = new ArrayList<>();
    }

    public List<Order> getOrders() {
        return orders;
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

    public CategoryEvent getCategoryEvent() {
        return categoryEvent;
    }

    public GeographicalArea getLocation() {
        return location;
    }

    public EventQueue getEventQueue() {
        return eventQueue;
    }
}