package domain.event;

import domain.policy.*;

import java.util.Date;
import java.util.List;

public class Event {
    private int companyId;
    private int creatorId;
    private EventMap eventMap;
    private EventQueue eventQueue;
    private Date date;
    private String name;
    private Date saleStartDate;
    private boolean hasLottery;
    private Purcase purchasePolicy;
    private Discount discountPolicy;


    public Event(int companyId, int creatorId, Date date, String name, Date saleStartDate, boolean hasLottery) {
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
       return saleStartDate.before(new Date()) && eventMap != null;
    }
}