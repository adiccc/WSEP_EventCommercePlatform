
package application;

import app.config.SystemProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class TokenService {

   private final long expirationTime; // 24 hours
    private final SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    @Autowired
    public TokenService(SystemProperties systemProperties) {
        this.expirationTime = systemProperties.getTokenExpirationHours() * 60L * 60L * 1000L;
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", "MEMBER")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key)
                .compact();
    }

    public String generateGuestToken() {
        String uuid = java.util.UUID.randomUUID().toString();
        return Jwts.builder()
                .setSubject("GUEST_" + uuid)
                .claim("role", "GUEST")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    public Date extractExpirationDate(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    public String generateExpiredTokenForTest(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", "MEMBER")
                .setIssuedAt(new Date(System.currentTimeMillis() - (1000 * 60 * 60 * 2)))
                .setExpiration(new Date(System.currentTimeMillis() - (1000 * 60 * 60)))
                .signWith(key)
                .compact();
    }

}