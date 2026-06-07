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
public class GetUserIdHandler implements InitOperationHandler {

    @Autowired
    private UserService userService;

    @Override
    public String operationType() { return "get-user-id"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        Response<Integer> response = userService.getUserId(params.get("token"));
        if (response.getValue() == null)
            throw new InitializationException("get-user-id failed: " + response.getMessage());
        return response.getValue();
    }
}
