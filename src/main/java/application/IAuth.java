package application;

public interface IAuth {
    Response<String> login(String username, String password);
    Response<Boolean> logout(String token);
    Response<Boolean> isLoggedIn(String token);
    Response<Integer> getUserId(String token);
    Response<Boolean> isAdmin(String token);
}
