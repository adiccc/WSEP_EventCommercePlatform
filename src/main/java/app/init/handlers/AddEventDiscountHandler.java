package app.init.handlers;

import DTO.DiscountDTO;
import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.EventCompanyManageService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class AddEventDiscountHandler implements InitOperationHandler {

    @Autowired
    private EventCompanyManageService eventService;

    @Override
    public String operationType() { return "add-event-discount"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token   = params.get("token");
        int    eventId = Integer.parseInt(params.get("eventId"));
        String code    = params.get("couponCode");
        double percent = Double.parseDouble(params.get("percent"));
        LocalDate expiry = LocalDate.now().plusDays(Long.parseLong(params.get("expiryDaysFromNow")));

        Response<Boolean> response = eventService.addDiscountToEvent(token, eventId, new DiscountDTO(code, percent, expiry));
        if (response.getValue() == null)
            throw new InitializationException("add-event-discount failed: " + response.getMessage());
        return true;
    }
}
