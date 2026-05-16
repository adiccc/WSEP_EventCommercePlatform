package domain.dto;


import domain.Suspension.Suspension;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class SuspensionDTO {
    private int userId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public SuspensionDTO(int userId, LocalDateTime startDate, LocalDateTime endDate) {
        this.userId = userId;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    public SuspensionDTO(Suspension suspension) {
        this.userId = suspension.getUserId();
        this.startDate=suspension.getStartDate();
        this.endDate=suspension.getEndDate();
    }
    public int getUserId() {
        return userId;
    }
    public LocalDateTime getStartDate() {
        return startDate;
    }
    public LocalDateTime getEndDate() {
        return endDate;
    }

}
