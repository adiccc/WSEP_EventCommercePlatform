package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.EventCompanyManageService;
import application.Response;
import domain.dataType.CategoryEvent;
import domain.dataType.GeographicalArea;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class CreateEventHandler implements InitOperationHandler {

    @Autowired
    private EventCompanyManageService eventService;

    @Override
    public String operationType() { return "create-event"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token      = params.get("token");
        int companyId     = Integer.parseInt(params.get("companyId"));
        String name       = params.get("name");
        LocalDateTime date      = parseRelativeDate(params.get("date"));
        LocalDateTime saleStart = parseRelativeDate(params.getOrDefault("saleStart", "-1D"));
        boolean hasLottery      = Boolean.parseBoolean(params.getOrDefault("hasLottery", "false"));
        GeographicalArea area   = GeographicalArea.valueOf(params.get("area"));
        CategoryEvent category  = CategoryEvent.valueOf(params.get("category"));

        Response<Integer> response = eventService.createEvent(token, companyId, date, name, saleStart, hasLottery, area, category);
        if (response.getValue() == null)
            throw new InitializationException("create-event failed [" + name + "]: " + response.getMessage());
        return response.getValue();
    }

    // Parses relative date specs: +1M, +20D, +1W, +3m (minutes), -1D, -55m
    static LocalDateTime parseRelativeDate(String spec) {
        LocalDateTime now = LocalDateTime.now();
        if (spec == null || spec.isBlank()) return now;
        char sign = spec.charAt(0);
        if (sign != '+' && sign != '-') return now;
        int multiplier = (sign == '+') ? 1 : -1;
        String rest = spec.substring(1);
        char unit = rest.charAt(rest.length() - 1);
        int amount = Integer.parseInt(rest.substring(0, rest.length() - 1)) * multiplier;
        return switch (unit) {
            case 'M' -> now.plusMonths(amount);
            case 'W' -> now.plusWeeks(amount);
            case 'D' -> now.plusDays(amount);
            case 'm' -> now.plusMinutes(amount);
            default  -> now;
        };
    }
}
