package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.Response;
import application.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LogoutHandler implements InitOperationHandler {

    @Autowired
    private UserService userService;

    @Override
    public String operationType() { return "logout"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<Boolean> response = userService.logout(params.get("token"));
        if (response.getValue() == null)
            throw new InitializationException("logout failed: " + response.getMessage());
        return response.getValue();
    }
}
