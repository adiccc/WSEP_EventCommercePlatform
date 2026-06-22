package DTO;

import java.util.List;

public class SalesReportDTO {
    private int companyId;
    private double totalRevenue; //the total income
    private int totalTicketsSold; // total tickets
    private List<EventSalesRecordDTO> eventRecords; //information for each event

    public SalesReportDTO(int companyId, double totalRevenue, int totalTicketsSold, List<EventSalesRecordDTO> eventRecords) {
        this.companyId = companyId;
        this.totalRevenue = totalRevenue;
        this.totalTicketsSold = totalTicketsSold;
        this.eventRecords = eventRecords;
    }

    public int getCompanyId() { return companyId; }
    public double getTotalRevenue() { return totalRevenue; }
    public int getTotalTicketsSold() { return totalTicketsSold; }
    public List<EventSalesRecordDTO> getEventRecords() { return eventRecords; }
}