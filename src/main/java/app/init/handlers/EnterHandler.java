package app.init.handlers;

import DTO.QueueEntryResultDTO;
import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.Response;
import application.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EnterHandler implements InitOperationHandler {

    @Autowired
    private UserService userService;

    @Override
    public String operationType() { return "enter"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<QueueEntryResultDTO> response = userService.enter();
        if (response.getValue() == null)
            throw new InitializationException("enter failed: " + response.getMessage());
        QueueEntryResultDTO result = response.getValue();
        if (!result.isAdmitted())
            throw new InitializationException("enter failed: system queue is full during initialization");
        return result.getToken();
    }
}
