package domain.dataType;

import DTO.ElementPositionDTO;

public abstract class Zone {
    private String name;
    private double price;
    private ElementPosition elementPosition;

    public Zone(String name, double price, ElementPosition elementPosition) {
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;
    }
    public Zone(String name, double price, ElementPositionDTO elementPosition) {
        this.name = name;
        this.price = price;
        this.elementPosition = new ElementPosition(elementPosition.getX(),elementPosition.getY());
    }

    public String getName() {
        return name;
    }
    public ElementPosition getElementPosition() {
        return new ElementPosition(elementPosition);
    }

    public double getPrice() {
        return price;
    }
}
