package domain.lottery;

import java.security.SecureRandom;

public final class AccessCodeGenerator {

    private static final String CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessCodeGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
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