package DTO;

import domain.dataType.ElementPosition;
import domain.dataType.TicketStatus;

import java.util.HashMap;
import java.util.Map;

public class SeatingZoneDTO {
    private int zoneId;
    private String name;
    private double price;
    private ElementPositionDTO elementPosition;
    private int rows;
    private int cols;
    private Map<String, TicketStatus> ticketStatuses;

    public SeatingZoneDTO(
            int zoneId,
            int rows,
            int cols,
            String name,
            double price,
            ElementPositionDTO elementPosition
    ) {
        this(zoneId, rows, cols, name, price, elementPosition, new HashMap<>());
    }

    public SeatingZoneDTO(
            int zoneId,
            int rows,
            int cols,
            String name,
            double price,
            ElementPositionDTO elementPosition,
            Map<String, TicketStatus> ticketStatuses
    ) {
        this.zoneId = zoneId;
        this.rows = rows;
        this.cols = cols;
        this.name = name;
        this.price = price;
        this.elementPosition = elementPosition;
        this.ticketStatuses = ticketStatuses;
    }

    public int getZoneId() {return zoneId;}

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

    public Map<String, TicketStatus> getTicketStatuses() {
        return ticketStatuses;
    }

    public TicketStatus getTicketStatus(int row, int col) {
        if (ticketStatuses == null) {
            return TicketStatus.AVAILABLE;
        }

        return ticketStatuses.getOrDefault(
                row + "-" + col,
                TicketStatus.AVAILABLE
        );
    }
}