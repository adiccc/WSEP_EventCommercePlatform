package UI.Presenters;

import application.UserService;
import DTO.UserDTO;
import application.Response;

public class RegisterPresenter {

    private final UserService userService;

    public RegisterPresenter(UserService userService) {
        this.userService = userService;
    }

    public Response<Boolean> register(UserDTO dto) {
        return userService.registerUser(null, dto);
    }
}