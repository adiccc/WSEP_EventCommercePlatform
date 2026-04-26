package infrastructure;

import application.IAuth;
import application.IPasswordEncoder;
import application.Response;
import application.TokenService;
import domain.user.IUserRepo;
import domain.user.Member;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Auth implements IAuth {
    private static final Logger logger = Logger.getLogger(Auth.class.getName());
    private final TokenService tokenService;
    private final IUserRepo userRepo;
    private final IPasswordEncoder passwordEncoder;
    private final Map<String, Date> tokensLoggedOut = new ConcurrentHashMap<>();

    public Auth(TokenService tokenService, IUserRepo userRepo, IPasswordEncoder passwordEncoder) {
        this.tokenService = tokenService;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Response<String> login(String username, String password) {
        logger.info("Login attempt for username: " + username);
        try {
            Member member = userRepo.findUserByEmail(username);
            if (member == null || !passwordEncoder.matches(password, member.getPassword())) {
                logger.warning("Login failed: Invalid credentials for " + username);
                return new Response<>(null,"Invalid email or password");
            }
            String token = tokenService.generateToken(username);
            logger.info("Login successful for username: " + username);
            return new Response<>(token, "Login successful");

        } catch (Exception e) {
            logger.severe("Login failed for username: " + username + ". Error: " + e.getMessage());
            return new Response<>(null,"Login failed due to server error");
        }
    }
    @Override
    public Response<Boolean> logout(String token) {
        logger.info("Logout attempt");
        if (token == null || token.isBlank()) {
            return new Response<>(false, "Token is missing or empty");
        }
        if(tokensLoggedOut.containsKey(token)) {
            logger.warning("Logout attempt failed: member is in the logged out tokens list") ;
            return new Response<>(false, "Cannot log out, user is Already logged out");
        }
            try{
                Date date = tokenService.extractExpirationDate(token);
                int userId = getUserId(token).getValue();
                tokensLoggedOut.put(token, date);
                cleanExpiredLoggedOutTokens();
                logger.info("Logout successful for username: " + userId);
                //TODO after successful logout need to notify webQueue to insert
                return new Response<>(true, "Logout successful");
            }
            catch(Exception e){
                logger.severe("Logout failed for token: " + token + ". Error: " + e.getMessage());
                return new Response<>(false, "Logout failed due to server error");
            }
        }
    private void cleanExpiredLoggedOutTokens() {
        Date today = new Date();
        logger.info("Clean expired logged out tokens");
        for(Map.Entry<String, Date> entry : tokensLoggedOut.entrySet()){
            if(entry.getValue().before(today)){
                tokensLoggedOut.remove(entry.getKey());
            }
        }
        logger.info("Cleaned expired logged out tokens successfully");
    }

    @Override
    public Response<Boolean> isLoggedIn(String token) {
        if(token == null){
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
        }
        catch (Exception e){
            logger.severe("Member is not logged in due to server error");
            return new Response<>(false, "Member is not logged in due to server error");
        }
    }

    @Override
    public Response<Integer> getUserId(String token) {
        if (!isLoggedIn(token).getValue()) {
            logger.warning("User with token is not logged in");
            return new Response<>(-1, "User with token is not logged in");
        }
        try {
            String username = tokenService.extractUsername(token);
            if (username == null) {
                logger.warning("User with token is not found");
                return new Response<>(-1, "User with token is not found");
            }

            Member member = userRepo.findUserByEmail(username);
            if (member != null) {
                logger.info("Retrieved member " + member.getIdentifier());
                return new Response<>(member.getUserId(), "Retrieved member");
            }
            else {
                logger.warning("Member is not found");
                return new Response<>(-1, "Member is not found");
            }

        } catch (Exception e) {
            logger.severe("User is not found");
            return new Response<>(-1, "User is not found");
        }
    }
}
