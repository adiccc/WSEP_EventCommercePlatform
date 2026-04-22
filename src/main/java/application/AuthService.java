package application;

import domain.user.IUserRepo;

import java.util.logging.Logger;

public class AuthService implements IAuth {
    private static final Logger logger = Logger.getLogger(AuthService.class.getName());

    private final TokenService tokenService;
    private final IUserRepo userRepo;

    public AuthService(TokenService tokenService, IUserRepo userRepo) {
        this.tokenService = tokenService;
        this.userRepo = userRepo;
    }

    @Override
    public Response<String> login(String username, String password) {
        logger.info("Login attempt for username: " + username);
        try {
            // TODO: validate credentials against userRepo
            String token = tokenService.generateToken(username);
            logger.info("Login successful for username: " + username);
            return Response.ok(token);
        } catch (Exception e) {
            logger.severe("Login failed for username: " + username + ". Error: " + e.getMessage());
            return Response.error("Login failed for username: " + username);
        }

    }

    @Override
    public boolean isLoggedIn(String token) {
        return tokenService.validateToken(token);
    }

    @Override
    public int getUserId(String token) {
        if (!tokenService.validateToken(token))
            throw new SecurityException("Invalid or expired token");
        // TODO: resolve username to userId via userRepo
        String username = tokenService.extractUsername(token);
        return username.hashCode();
    }
}
