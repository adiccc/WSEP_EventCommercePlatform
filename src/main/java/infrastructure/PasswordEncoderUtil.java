package infrastructure;

import application.IPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderUtil implements IPasswordEncoder {
    private final PasswordEncoder bCryptEncoder = new BCryptPasswordEncoder();

    @Override
    public String encodePassword(String password) {
        return bCryptEncoder.encode(password);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return bCryptEncoder.matches(rawPassword, encodedPassword);
    }
}