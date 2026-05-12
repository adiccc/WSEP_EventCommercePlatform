package domain.event;

import domain.dataType.CategoryEvent;
import domain.dataType.EventSearchFilter;
import domain.dataType.GeographicalArea;
import domain.dto.SeatingTicketDTO;
import domain.dto.UserDTO;
import domain.policy.*;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

public class Event {
    private int id;
    private int companyId;
    private int creatorId;
    private EventMap eventMap;
    private EventQueue eventQueue;
    private LocalDateTime date;
    private String name;
    private LocalDateTime saleStartDate;
    private boolean hasLottery;
    private PurchasePolicy purchasePolicy;
    private DiscountPolicy discountPolicy;
    private boolean active;
    private GeographicalArea location;
    private CategoryEvent categoryEvent;
    private List<Order> orders;
    private long version;

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
        this.id = -1; // will be set when stored in repo
        active = false;
        this.location = location;
        this.categoryEvent = categoryEvent;
        this.eventQueue = new EventQueue();
        this.orders = new ArrayList<>();
        this.version = 0;
    }

    public Event(Event event){
        if(event.eventMap==null){
            this.eventMap=null;
        }else{
            this.eventMap = new EventMap(event.eventMap);
        }
        this.companyId = event.companyId;
        this.creatorId = event.creatorId;
        this.date = event.date;
        this.name = event.name;
        this.saleStartDate = event.saleStartDate;
        this.hasLottery = event.hasLottery;
        this.purchasePolicy=new PurchasePolicy(event.purchasePolicy);
        this.discountPolicy=new DiscountPolicy(event.discountPolicy);
        this.id=event.id;
        this.eventQueue = new EventQueue(event.eventQueue);
        this.active = event.active;
        this.location = event.location;
        this.categoryEvent = event.categoryEvent;
        this.orders=new ArrayList<>();
        for(Order order : event.orders){
            this.orders.add(new Order(order));
        }
        this.version = event.version;
    }

    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    public List<Order> getOrders() {
         if(orders==null){
             orders = new ArrayList<>();
         }
         return orders;
    }
    public Order findOrderById(int orderId) {
        for (Order order : orders) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        return null;
    }

    public int getCompanyId() {
        return companyId;
    }
    public int getCreatorId(){
        return creatorId;
    }
    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
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

    public int getId() {
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

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public EventMap getEventMap() {
        return eventMap;
    }

    public boolean isFuture() {
        return date.isAfter(LocalDateTime.now());
    }

    public boolean matches(EventSearchFilter filter) {
        if (!isActive() || !isFuture()) return false;

        if (filter.getKeyword() != null && !filter.getKeyword().isEmpty()) {
            if (!name.toLowerCase().contains(filter.getKeyword().toLowerCase()))
                return false;
        }

        if (filter.getStartDate() != null && date.isBefore(filter.getStartDate()))
            return false;

        if (filter.getEndDate() != null && date.isAfter(filter.getEndDate()))
            return false;

        if (filter.getCategory() != null && categoryEvent != filter.getCategory())
            return false;

        if (filter.getLocation() != null && location != filter.getLocation())
            return false;

        if (!matchesPrice(filter))
            return false;

        return true;
    }


    private boolean matchesPrice(EventSearchFilter filter) {
        if (filter.getMinPrice() == null && filter.getMaxPrice() == null)
            return true;

        return eventMap.getZones().stream().anyMatch(z -> {
            double price = z.getPrice();

            if (filter.getMinPrice() != null && price < filter.getMinPrice())
                return false;

            if (filter.getMaxPrice() != null && price > filter.getMaxPrice())
                return false;

            return true;
        });
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Event other = (Event) obj;
        return id == (other.id) && version == other.version;
    }

    public void quantityExceedsPolicy(UserDTO user, int quantity) {
        int ticketsBoughtForEvent =0;
        for (Order order : orders) {
            if (order.getUserIdentifier().equals(user.getEmail())) {
                ticketsBoughtForEvent += order.getTickets().size();
            }
        }
        if( !purchasePolicy.isSatisfied(user, quantity,ticketsBoughtForEvent)) {
            throw new IllegalArgumentException("Purchase policy not satisfied for user " + user.getUserId() + " and quantity " + quantity);
        }
    }

    public List<Integer> bookTickets(Map<String, List<SeatingTicketDTO>> seatingZones, Map<String, Integer> standingZones) {
        return eventMap.bookTickets(seatingZones,standingZones);
    }
    public double calculateFinalTotalPrice(List<Integer> ticketIds, String couponCode) {
        if (eventMap == null) {
            throw new IllegalStateException("Event map is not defined");
        }

        double priceBeforeDiscount = eventMap.calculateTotalPriceBeforeDiscount(ticketIds);

        if (discountPolicy == null) {
            return priceBeforeDiscount;
        }

        return discountPolicy.apply(priceBeforeDiscount, ticketIds.size(), couponCode);
    }

    public void setId(int id) {
        this.id = id;
    }

    public void releaseTickets(List<Integer> ticketIds) {
        eventMap.releaseTickets(ticketIds);
    }

    public int countStandingInZone(String zone, List<Integer> currentTickets) {
        return eventMap.countStandingInZone(zone,currentTickets);
    }

    public List<Integer> findSeatingTicketIds(Map<String, List<SeatingTicketDTO>> seatingToRemove) {
        List<Integer> seatingTicketIds = new ArrayList<>();
        for (Map.Entry<String, List<SeatingTicketDTO>> entry : seatingToRemove.entrySet()) {
            String zoneName = entry.getKey();
            List<SeatingTicketDTO> seats = entry.getValue();
            Zone matchedZone = null;
            for (Zone z : eventMap.getZones()) {
                if (z.getName().equals(zoneName)) {
                    matchedZone = z;
                    break;
                }
            }
            if (matchedZone == null) {
                throw new IllegalArgumentException("Seating zone does not exist: " + zoneName);
            }
            if (!(matchedZone instanceof SeatingZone)) {
                throw new IllegalArgumentException("Zone is not a seating zone: " + zoneName);
            }
            seatingTicketIds.addAll(((SeatingZone) matchedZone).findSeatingTicketIds(seats));
        }
        return seatingTicketIds;
    }

    public List<Integer> pickStandingFromZone(String zone, List<Integer> newTickets, int numToPick) {
        Zone matchedZone = null;
        for (Zone z : eventMap.getZones()) {
            if (z.getName().equals(zone)) {
                matchedZone = z;
                break;
            }
        }
        if (matchedZone == null) {
            throw new IllegalArgumentException("Standing zone does not exist: " + zone);
        }
        if (!(matchedZone instanceof StandingZone)) {
            throw new IllegalArgumentException("Zone is not a standing zone: " + zone);
        }
        return ((StandingZone) matchedZone).pickStandingFromZone(newTickets, numToPick);
    }

    public void markTicketsAsSold(List<Integer> ticketIds) {
        eventMap.markTicketsAsSold(ticketIds);
    }
}