package application;

public interface IAuth {
    Response<String> login(String username); //email

    Response<Boolean> logout(String token);

    Response<Boolean> isLoggedIn(String token);

    Response<Boolean> isAdmin(String token);

    Response<String> getRole(String token);

    Response<String> getUserEmail(String token);

    Response<String> getUserIdentifier(String token);

}
