package app.init.handlers;

import app.init.InitContext;
import app.init.InitOperationHandler;
import app.init.InitializationException;
import application.Response;
import application.UserService;
import domain.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RegisterHandler implements InitOperationHandler {

    @Autowired
    private UserService userService;

    @Override
    public String operationType() { return "register"; }

    @Override
    public Object execute(Map<String, String> params, InitContext context) {
        UserDTO dto = new UserDTO(
                params.get("email"),
                params.get("firstName"),
                params.get("lastName"),
                params.get("password"),
                Integer.parseInt(params.get("birthDay")),
                Integer.parseInt(params.get("birthMonth")),
                Integer.parseInt(params.get("birthYear")),
                params.get("address"),
                params.get("phone")
        );
        Response<Boolean> response = userService.registerUser(params.get("guestToken"), dto);
        if (response.isError())
            throw new InitializationException("register failed: " + response.getMessage());
        return true;
    }
}
