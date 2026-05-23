package infrastructure;

import application.IAuth;
import application.IPasswordEncoder;
import application.Response;
import application.TokenService;
import domain.dto.UserDTO;
import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Component
public class Auth implements IAuth {
    private static final Logger logger = Logger.getLogger(Auth.class.getName());
    private final TokenService tokenService;
    private final IUserRepo userRepo;
    private final IPasswordEncoder passwordEncoder;
    private final Map<String, Date> tokensLoggedOut = new ConcurrentHashMap<>(); // only for members
    private final Set<String> adminEmails;

    public Auth(TokenService tokenService, IUserRepo userRepo, IPasswordEncoder passwordEncoder,
            Set<String> adminEmails) {
        this.tokenService = tokenService;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.adminEmails = adminEmails;
    }

    @Autowired
    public Auth(TokenService tokenService, IUserRepo userRepo, IPasswordEncoder passwordEncoder) {
        this(tokenService, userRepo, passwordEncoder, Set.of());
    }

    @Override
    public Response<String> login(String username, String password) { // login only for members!
        logger.info("Login attempt for username: " + username);
        try {
            Member member = userRepo.findUserByEmail(username);
            if (member == null || !passwordEncoder.matches(password, member.getPassword())) {
                logger.warning("Login failed: Invalid credentials for " + username);
                return new Response<>(null, "Invalid email or password");
            }
            if (!member.isActive()) {
                logger.warning("Login failed: member is blocked by Admin");
                return new Response<>(null, "Login failed: member is blocked by Admin");
            }
            String token = tokenService.generateToken(username);
            logger.info("Login successful for username: " + username);
            return new Response<>(token, "Login successful");

        } catch (Exception e) {
            logger.severe("Login failed for username: " + username + ". Error: " + e.getMessage());
            return new Response<>(null, "Login failed due to server error");
        }
    }

    @Override
    public Response<Boolean> logout(String token) {
        logger.info("Logout attempt");
        if (token == null || token.isBlank()) {
            return new Response<>(false, "Token is missing or empty");
        }
        try {
            Date date = tokenService.extractExpirationDate(token);
            int userId = getUserId(token).getValue();
            if (tokensLoggedOut.putIfAbsent(token, date) != null) {
                logger.warning("Logout attempt failed: member is in the logged out tokens list");
                return new Response<>(false, "Cannot log out, user is Already logged out");
            }
            cleanExpiredLoggedOutTokens();
            logger.info("Logout successful for username: " + userId);
            return new Response<>(true, "Logout successful");
        } catch (Exception e) {
            logger.severe("Logout failed for token: " + token + ". Error: " + e.getMessage());
            return new Response<>(false, "Logout failed due to server error");
        }
    }

    @Override
    public Response<Boolean> isAdmin(String token) {
        if (!isLoggedIn(token).getValue()) {
            logger.warning("isAdmin check failed: token is not logged in");
            return new Response<>(false, "User is not logged in");
        }
        try {
            String email = tokenService.extractUsername(token);
            boolean admin = email != null && adminEmails.contains(email);
            return new Response<>(admin, admin ? "User is admin" : "User is not admin");
        } catch (Exception e) {
            logger.severe("isAdmin check failed due to server error: " + e.getMessage());
            return new Response<>(false, "Server error during admin check");
        }
    }

    @Override
    public Response<String> getRole(String token) {
        logger.info("trying to extract role");
        if (token == null || token.isBlank()) {
            logger.warning("token is missing or empty");
            return new Response<>(null, "Token is missing or empty");
        }
        try {
            if (!tokenService.validateToken(token)) {
                logger.warning("Token validation failed");
                return new Response<>(null, "Invalid or expired token");
            }
            String role = tokenService.extractRole(token);
            if ("MEMBER".equals(role)) {
                if (!isLoggedIn(token).getValue()) {
                    logger.warning("Token belongs to a logged-out member");
                    return new Response<>(null, "Member is logged out");
                }
            }
            // in guest extract if it's succesfull we check if the token is valid with
            // expiration date
            logger.info("retrieved and validate role: " + role);
            return new Response<>(role, "retrieved role");
        } catch (Exception e) {
            logger.severe("getRole failed for token: " + token + ". Error: " + e.getMessage());
            return new Response<>(null, "getRole failed due to server error");
        }
    }

    private void cleanExpiredLoggedOutTokens() {
        Date today = new Date();
        logger.info("Clean expired logged out tokens");
        for (Map.Entry<String, Date> entry : tokensLoggedOut.entrySet()) {
            if (entry.getValue().before(today)) {
                tokensLoggedOut.remove(entry.getKey());
            }
        }
        logger.info("Cleaned expired logged out tokens successfully");
    }

    @Override
    public Response<Boolean> isLoggedIn(String token) {
        if (token == null || token.isBlank()) {
            logger.warning("Token is null");
            return new Response<>(false, "Token is null");
        }
        try {

            if (!tokensLoggedOut.containsKey(token) && tokenService.validateToken(token)) {
                logger.info("Member is logged in");
                return new Response<>(true, "Member is logged in");
            } else {
                logger.warning("Member is logged out");
                return new Response<>(false, "Member is logged out");
            }
        } catch (Exception e) {
            logger.severe("Member is not logged in due to server error");
            return new Response<>(false, "Member is not logged in due to server error");
        }
    }

    @Override
    public Response<Integer> getUserId(String token) {
        if (token == null || token.isBlank()) {
            logger.warning("Token is missing");
            return new Response<>(-1, "Token is missing");
        }
        try {
            String role = tokenService.extractRole(token);
            if (role.equals("GUEST")) {
                logger.info("Token is belong to GUEST, returning -1");
                return new Response<>(-1, "Guest token recognized");
            }
            if (!isLoggedIn(token).getValue()) {
                logger.warning("User with token is not logged in");
                return new Response<>(-1, "User with token is not logged in");
            }
            String username = tokenService.extractUsername(token);
            if (username == null) {
                logger.warning("User with token is not found");
                return new Response<>(-1, "User with token is not found");
            }

            Member member = userRepo.findUserByEmail(username);
            if (member != null) {
                logger.info("Retrieved member " + member.getIdentifier());
                return new Response<>(member.getUserId(), "Retrieved member");
            } else {
                logger.warning("Member is not found");
                return new Response<>(-1, "Member is not found");
            }

        } catch (Exception e) {
            logger.severe("User is not found");
            return new Response<>(-1, "User is not found");
        }
    }

    public Response<UserDTO> getUserDTO(String token) {
        if (!isLoggedIn(token).getValue()) {
            logger.warning("User with token is not logged in");
            return new Response<>(null, "User with token is not logged in");
        }
        try {
            String username = tokenService.extractUsername(token);
            if (username == null) {
                logger.warning("User with token is not found");
                return new Response<>(null, "User with token is not found");
            }

            Member member = userRepo.findUserByEmail(username);
            if (member != null) {
                logger.info("Retrieved member " + member.getIdentifier());
                UserDTO userDTO = member.getUserDTO();
                return new Response<>(userDTO, "Retrieved member");
            } else {
                logger.warning("Member is not found");
                return new Response<>(null, "Member is not found");
            }

        } catch (Exception e) {
            logger.severe("User is not found");
            return new Response<>(null, "User is not found");
        }
    }

    public Response<String> getUserEmail(String token) {
        if (token == null || token.isBlank()) {
            logger.warning("Token is missing");
            return new Response<>(null, "Token is missing");
        }
        try {
            String role = tokenService.extractRole(token);
            if (role.equals("GUEST")) {
                logger.info("Token is belong to GUEST, returning -1");
                return new Response<>(null, "Guest token recognized");
            }
            if (!isLoggedIn(token).getValue()) {
                logger.warning("User with token is not logged in");
                return new Response<>(null, "User with token is not logged in");
            }
            String username = tokenService.extractUsername(token);
            if (username == null) {
                logger.warning("User with token is not found");
                return new Response<>(null, "User with token is not found");
            }

            logger.info("Retrieved member's email " + username);
            return new Response<>(username, "Retrieved member");

        } catch (Exception e) {
            logger.severe("User is not found");
            return new Response<>(null, "User is not found");
        }
    }

    @Override
    public Response<String> getUserIdentifier(String token) { // extracting Identifier for notifications
        if (token == null || token.isBlank()) {
            logger.warning("Token is missing");
            return new Response<>(null, "Token is missing");
        }
        try {
            String role = tokenService.extractRole(token);
            if (role.equals("GUEST") || role.equals("MEMBER")) {
                String identifier = tokenService.extractUsername(token);
                logger.info("Retrieved user identifier " + identifier);
                return new Response<>(identifier, "Guest token recognized");
            } else {
                logger.warning("User with token is not found");
                return new Response<>(null, "User with token is not found");
            }
        } catch (Exception e) {
            logger.severe("User is not found");
            return new Response<>(null, "User is not found");
        }
    }

}
