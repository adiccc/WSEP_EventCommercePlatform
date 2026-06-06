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
public class LoginHandler implements InitOperationHandler {

    @Autowired
    private UserService userService;

    @Override
    public String operationType() { return "login"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<String> response = userService.login(params.get("email"), params.get("password"));
        if (response.isError())
            throw new InitializationException("login failed: " + response.getMessage());
        return response.getValue();
    }
}
