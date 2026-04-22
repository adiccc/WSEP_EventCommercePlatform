package application;

public interface IAuth {
    Response<String> login(String username, String password);
    boolean isLoggedIn(String token);
    int getUserId(String token);
}
