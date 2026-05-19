package application;

import DTO.NotifyDTO;
import DTO.QueueEntryResultDTO;
import domain.dto.UserDTO;
import domain.user.*;
import Exception.OptimisticLockingFailureException;
import domain.webQueue.WebQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());
    private final TokenService tokenService;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;
    private final IUserRepo userRepo;
    private final INotifier notifier;

    @Autowired
    public UserService(TokenService tokenService, IAuth auth, IUserRepo userRepo,
                       IPasswordEncoder passwordEncoder, INotifier notifier) {
        this.tokenService = tokenService;
        this.auth = auth;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
    }

    // first call when a user opens the application
    // if admitted: uuid serves as the guest sessionId
    // if waiting: uuid is the queue token to poll with
    public Response<QueueEntryResultDTO> enter() {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("New user entering the system");
            QueueEntryResultDTO result = WebQueue.getInstance().tryEnter(uuid -> onUserAdmitted(uuid));
            if (result.isAdmitted()) {
                logger.info("User admitted immediately with sessionId: " + result.getToken());
            } else {
                logger.info("User placed in queue at position: " + result.getPosition());
            }
            return Response.ok(result);
        });
    }

    // client polls this while waiting in the queue
    public Response<QueueEntryResultDTO> getQueueStatus(String uuid) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("Queue status requested for token: " + uuid);
            QueueEntryResultDTO result = WebQueue.getInstance().getStatus(uuid);
            if (result.isAdmitted()) {
                logger.info("User with token " + uuid + " is now admitted");
            }
            return Response.ok(result);
        });
    }

    // fired by WebQueue when a waiting user is admitted — uuid becomes their guest sessionId
    //trigger for moving to login page //::TO DO!
    private void onUserAdmitted(String uuid) {
        logger.info("User admitted from queue with sessionId: " + uuid);
    }

    public Response<String> continueAsGuest() {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("User requested to continue in the website as guest with UUID");
            try {
                String newGuestSessionId = tokenService.generateGuestToken();
                logger.info("Guest session fully initialized.");
                return new Response<>(newGuestSessionId, "Guest session created successfully.");

            }
            catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Failed to initialize guest session. Error: " + e.getMessage());
                return new Response<>(null,"Server error while continuing as guest: " + e.getMessage());
            }
        });
    }

    public Response<Boolean> registerUser(String activeIdentifier, UserDTO dto) {
        return RetryHelper.executeWithRetry(() ->
        {
            logger.info("Registration attempt started for email: " + dto.getEmail());
            try {
                if(activeIdentifier!=null && !activeIdentifier.isBlank()) {
                    int currentUserId = auth.getUserId(activeIdentifier).getValue();
                    if (currentUserId != -1) { //in member state
                        logger.warning("Registration failed: Active logged-in user attempted to register (Token: " + activeIdentifier + ")");
                        return Response.error("Can't register to the system at member state, must be a guest.");
                    }
                }
                String email = dto.getEmail();
                if (email != null && userRepo.existsUser(email)) {
                    logger.warning("Registration failed: User with email " + email + " already exists");
                    return Response.error("User " + email + " already exists");
                }
                LocalDate birthDate = null;
                try {
                    birthDate = LocalDate.of(dto.getYear(), dto.getMonth(), dto.getDay());
                } catch (DateTimeException e) {
                    logger.warning("Registration failed for " + email + ": Invalid date format");
                    return Response.error("Invalid format date");
                }
                if (birthDate.isAfter(LocalDate.now())) {
                    logger.warning("Registration failed for " + email + ": Future birth date provided");
                    return Response.error("birth date cannot be after current date");
                }
                String mailRegex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
                Pattern emailPattern = Pattern.compile(mailRegex);
                Matcher emailMatcher = emailPattern.matcher(email);
                if (!emailMatcher.matches()) {
                    logger.warning("Registration failed: Invalid email format (" + email + ")");
                    return Response.error("Invalid email format");
                }
                if (dto.getFirstName() == null || dto.getFirstName().isBlank()) {
                    logger.warning("Registration failed for " + email + ": First name is empty");
                    return Response.error("First name cannot be empty");
                }
                if (dto.getLastName() == null || dto.getLastName().isBlank()) {
                    logger.warning("Registration failed for " + email + ": Last name is empty");
                    return Response.error("Last name cannot be empty");
                }
                if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                    logger.warning("Registration failed for " + email + ": Password is empty");
                    return Response.error("Password cannot be empty");
                }
                if (dto.getAddress() == null || dto.getAddress().isBlank()) {
                    logger.warning("Registration failed for " + email + ": Address is empty");
                    return Response.error("Address cannot be null");
                }
                String phoneRegex = "^[0-9]{3}-[0-9]{3}-[0-9]{4}$";
                Pattern phonePattern = Pattern.compile(phoneRegex);
                Matcher phoneMatcher = phonePattern.matcher(dto.getPhone());
                if (!phoneMatcher.matches()) {
                    logger.warning("Registration failed for " + email + ": Invalid phone format");
                    return Response.error("Invalid phone format");
                }
                String encryptedPassword = passwordEncoder.encodePassword(dto.getPassword());
                Member member = new Member(
                        email,
                        encryptedPassword,
                        dto.getFirstName(),
                        dto.getLastName(),
                        dto.getPhone(),
                        birthDate,
                        dto.getAddress(),
                        ActivationStatus.ACTIVE);
                userRepo.store(member);
                logger.info("Registration successful for email: " + email);
                return Response.ok(true);
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Registration failed due to unexpected server error for email " + dto.getEmail() + ". Error: " + e.getMessage());
                return Response.error(e.getMessage());
            }
        });
    }

    public Response<String> login(String email, String password) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("Login attempt started for email: " + email);
            Response<String> tokenResponse = auth.login(email, password);
            if (tokenResponse.getValue() == null) {
                logger.warning("Login attempt failed for " + email);
                return Response.error(tokenResponse.getMessage());
            }
            logger.info("Login successful for email: " + email);
            return new Response<>(tokenResponse.getValue(), tokenResponse.getMessage());
        });
    }

    public Response<Boolean> logout(String token){
        return RetryHelper.executeWithRetry(() ->{
            logger.info("Logout attempt started for token: " + token);
            if (token == null || token.isBlank()) {
                logger.warning("token is empty or invalid");
                return new Response<>(false, "token is empty or invalid");
            }
            String role = auth.getRole(token).getValue();
            if(role!=null && role.equals("GUEST")){
               logger.warning("Logout attempt failed: guest cannot log out");
               return new Response<>(false, "User is in guest state");
            }//entering to the try while we know we are MEMBERs
            try {
                Response<Boolean> response = auth.logout(token);
                if (!response.getValue()) {
                    logger.warning("Logout attempt failed for " + token);
                    return new Response<>(false, "Logout failed");
                }
                WebQueue.getInstance().notifyUserLeft();
                logger.info("Logout successful for token: " + token);
                return response;
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("Logout failed due to unexpected server error for token: " + token);
                return Response.error(e.getMessage());
            }
        });
    }
    public Response<Boolean> leaveStore(String token) { //leave button for Guests!
        return RetryHelper.executeWithRetry(() -> {
            logger.info("leaveStore attempt started for token: " + token);
            if (token == null || token.isBlank()) {
                return new Response<>(false, "Invalid token");
            }
            String role = auth.getRole(token).getValue();
            if ("MEMBER".equals(role)) {
                logger.warning("leaveStore failed: only guests can use this function");
                return new Response<>(false, "Members should use logout, not leaveStore");
            }
            try {
                Response<Boolean> response = auth.logout(token);
                if (!response.getValue()) {
                    logger.warning("Logout attempt failed for " + token);
                    return new Response<>(false, "Logout failed");
                }
                WebQueue.getInstance().notifyUserLeft();
                logger.info("leaveStore successful for token: " + token);
                return response;
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("leaveStore failed: " + e.getMessage());
                return Response.error("Server error during leaveStore: " + e.getMessage());
            }
        });
    }
    public Response<List<NotifyDTO>> getDelayedNotifications(String userEmail) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("deliverDelayedNotifications attempt started for email: " + userEmail);
            if (userEmail == null || userEmail.isBlank()) {
                logger.warning("Failed to deliver notifications: Email is empty or null");
                return new Response<>(null, "Invalid email address");
            }
            try {
                Member member = userRepo.findUserByEmail(userEmail);
                List<NotifyDTO> allDelayedNotification = member.getDelayedNotifications();
                logger.info("deliverDelayedNotifications successful for email: " + userEmail);
                return new Response<>(allDelayedNotification, "Successfully processed delayed notifications");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("deliverDelayedNotifications failed: " + e.getMessage());
                return new Response<>(null, "Server error during deliverDelayedNotifications: " + e.getMessage());
            }
        });
    }
    public Response<Boolean> cleanDelayedNotifications(String userEmail) {
        return RetryHelper.executeWithRetry(() -> {
            logger.info("cleanDelayedNotifications attempt started for email: " + userEmail);
            if (userEmail == null || userEmail.isBlank()) {
                logger.warning("Failed to clean delayed notifications: Email is empty or null");
                return new Response<>(false, "Invalid email address");
            }
            try {
                Member member = userRepo.findUserByEmail(userEmail);
                if (member != null) {
                    member.clearDelayedNotifications();
                    userRepo.store(member);
                    logger.info("deliverDelayedNotifications successful for email: " + userEmail);
                    return new Response<>(true, "Successfully cleaned delayed notifications");
                }
                return new Response<>(false, "User not found");
            } catch (OptimisticLockingFailureException e) {
                throw e;
            } catch (Exception e) {
                logger.severe("cleanDelayedNotifications failed: " + e.getMessage());
                return new Response<>(false, "Server error during cleanDelayedNotifications: " + e.getMessage());
            }
        });
    }
}
