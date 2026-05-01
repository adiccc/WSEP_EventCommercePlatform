package DTO;

import java.util.ArrayList;
import java.util.List;

public class TicketSupplyResultDTO {
    private final boolean success;
    private final List<String> issuedCodes;

    public TicketSupplyResultDTO(boolean success, List<String> issuedCodes) {
        this.success = success;
        this.issuedCodes = issuedCodes == null ? new ArrayList<>() : new ArrayList<>(issuedCodes);
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getIssuedCodes() {
        return new ArrayList<>(issuedCodes);
    }
}