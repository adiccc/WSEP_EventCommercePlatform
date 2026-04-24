package application;

public interface IPasswordEncoder {
    String encodePassword(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
