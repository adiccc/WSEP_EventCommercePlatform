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
    public void logout(String token) {
        logger.info("Logout attempt");
        if(token != null && !token.isBlank()){
            try{
                int userId = getUserId(token);
                Date date = tokenService.extractExpirationDate(token);
                tokensLoggedOut.put(token, date);
                cleanExpiredLoggedOutTokens();
                logger.info("Logout successful for username: " + userId);

            }
            catch(Exception e){
                logger.severe("Logout failed for token: " + token + ". Error: " + e.getMessage());
            }
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
    public boolean isLoggedIn(String token) {
        if(token == null){
            logger.warning("Token is null");
            return false;
        }
        return !tokensLoggedOut.containsKey(token) && tokenService.validateToken(token);
    }

    @Override
    public int getUserId(String token) {
        if(!isLoggedIn(token)){
            logger.warning("User with token " + token + " is not logged in");
            return -1;
        }
        String username = tokenService.extractUsername(token);
        if(username == null){
            logger.warning("User with token " + token + " is not found");
            return -1;
        }
        Member member = userRepo.findUserByEmail(username);
        if(member != null){
            logger.info("Retrieved member " + member.getIdentifier() + " from token " + token);
            return member.getUserId();
        }
        return -1;
    }
}
