package application;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TokenService {

    private final long expirationTime = 1000 * 60 * 60 * 24; // 24 hours
    private final Map<String, String> tokenToUsername = new HashMap<>();
    private final Map<String, Date> tokenToExpiration = new HashMap<>();

    public String generateToken(String username) {
        String token = UUID.randomUUID().toString();
        tokenToUsername.put(token, username);
        tokenToExpiration.put(token, new Date(System.currentTimeMillis() + expirationTime));
        return token;
    }

    public boolean validateToken(String token) {
        if (!tokenToUsername.containsKey(token)) return false;
        return tokenToExpiration.get(token).after(new Date());
    }

    public String extractUsername(String token) {
        return tokenToUsername.get(token);
    }

    public Date extractExpiration(String token) {
        return tokenToExpiration.get(token);
    }

    public void invalidate(String token) {
        tokenToUsername.remove(token);
        tokenToExpiration.remove(token);
    }
}
