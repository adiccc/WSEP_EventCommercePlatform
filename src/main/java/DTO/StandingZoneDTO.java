package DTO;

public class StandingZoneDTO {
    private int capacty;
    private String name;
    private double price;
    private ElementPositionDTO elementPosition;
    public StandingZoneDTO(int capacty, String name, double price, ElementPositionDTO elementPosition) {
        this.capacty = capacty;
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;
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
}
