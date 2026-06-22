package DTO;

public class StandingZoneDTO {
    private int zoneId;
    private int capacty;
    private String name;
    private double price;
    private int available;
    private ElementPositionDTO elementPosition;

    public StandingZoneDTO(
            int zoneId,
            int capacity,
            String name,
            double price,
            ElementPositionDTO elementPosition
    ) {
        this(zoneId, capacity, capacity, name, price, elementPosition);
    }
    public StandingZoneDTO(
            int zoneId,
            int capacity,
            int available, //when returning mapDTO after booking, available might be less than capacity
            String name,
            double price,
            ElementPositionDTO elementPosition
    ) {
        this.zoneId = zoneId;
        this.capacty = capacity;
        this.available = available;
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;
    }

    public int getZoneId() {
        return zoneId;
    }
    public String getName() {
        return name;
    }
    public int getCapacty() {
        return capacty;
    }
    public double getPrice() {
        return price;
    }
    public ElementPositionDTO getPosition() {
        return elementPosition;
    }

    public int getAvailable() {
        return available;
    }
}
