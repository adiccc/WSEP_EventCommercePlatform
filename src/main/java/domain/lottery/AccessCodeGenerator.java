package domain.lottery;

import app.config.SystemProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
@Component
public class AccessCodeGenerator {

    private static String CHARACTERS;
    private static int CODE_LENGTH;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private SystemProperties systemProperties;

    private AccessCodeGenerator() {
    }

    @PostConstruct
    public void init() {
        CHARACTERS = systemProperties.getAccessCodeChars();
        CODE_LENGTH = systemProperties.getAccessCodeLength();
    }

    public static void configure(String chars, int length) {
        if (chars == null || chars.isBlank()) {
            throw new IllegalArgumentException("Access code characters cannot be empty");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Access code length must be positive");
        }

        CHARACTERS = chars;
        CODE_LENGTH = length;
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(randomIndex));
        }

        code.insert(3, "-");

        return code.toString();
    }
}