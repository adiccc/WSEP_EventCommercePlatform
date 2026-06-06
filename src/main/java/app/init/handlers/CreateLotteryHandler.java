package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.LotteryService;
import application.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class CreateLotteryHandler implements InitOperationHandler {

    @Autowired
    private LotteryService lotteryService;

    @Override
    public String operationType() { return "create-lottery"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        String token       = params.get("token");
        int    eventId     = Integer.parseInt(params.get("eventId"));
        int    capacity    = Integer.parseInt(params.get("capacity"));
        int    minutesFromNow  = Integer.parseInt(params.get("minutesFromNow"));
        long   expirationHours = Long.parseLong(params.get("expirationHours"));

        LocalDateTime registerWindow = LocalDateTime.now().plusMinutes(minutesFromNow);
        Response<Boolean> response = lotteryService.createLottery(token, eventId, capacity, registerWindow, expirationHours);
        if (response.getValue() == null)
            throw new InitializationException("create-lottery failed for event " + eventId + ": " + response.getMessage());
        return true;
    }
}
