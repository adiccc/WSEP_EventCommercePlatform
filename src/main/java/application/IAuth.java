package application;

public interface IAuth {
    Response<String> login(String username, String password);
    void logout(String token);
    boolean isLoggedIn(String token);
    int getUserId(String token);
}
