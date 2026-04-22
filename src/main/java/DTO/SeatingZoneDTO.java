package DTO;

import domain.dataType.ElementPosition;

public class SeatingZoneDTO {

    private String name;
    private double price;
    private ElementPositionDTO elementPosition;
    private int rows;
    private int cols;
    public SeatingZoneDTO(int rows, int cols, String name, double price, ElementPositionDTO elementPosition) {
        this.rows = rows;
        this.cols = cols;
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;

    }
    public String getName() {
        return name;
    }
    public double getPrice() {
        return price;
    }
    public ElementPositionDTO getPosition() {
        return elementPosition;
    }
    public int getRows() {
        return rows;
    }
    public int getCols() {
        return cols;
    }
}
