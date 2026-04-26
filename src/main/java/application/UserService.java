package application;
import domain.company.Company;
import domain.company.ICompanyRepo;
import DTO.QueueEntryResultDTO;
import domain.dto.UserDTO;
import domain.user.Member;
import domain.user.IUserRepo;
import application.IAuth;
import domain.user.User;
import domain.webQueue.WebQueue;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());
    private final TokenService tokenService;
    private final IAuth auth;
    private final IPasswordEncoder passwordEncoder;
    private final IUserRepo userRepo;
    private final ICompanyRepo companyRepo;

    public UserService(TokenService tokenService, IAuth auth, IUserRepo userRepo,
                       IPasswordEncoder passwordEncoder, ICompanyRepo companyRepo) {
        this.tokenService = tokenService;
        this.auth = auth;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.companyRepo = companyRepo;
    }

    // first call when a user opens the application
    // if admitted: uuid serves as the guest sessionId
    // if waiting: uuid is the queue token to poll with
    public Response<QueueEntryResultDTO> enter() {
        logger.info("New user entering the system");
        QueueEntryResultDTO result = WebQueue.getInstance().tryEnter(uuid -> onUserAdmitted(uuid));
        if (result.isAdmitted()) {
            logger.info("User admitted immediately with sessionId: " + result.getToken());
        } else {
            logger.info("User placed in queue at position: " + result.getPosition());
        }
        return Response.ok(result);
    }

    // client polls this while waiting in the queue
    public Response<QueueEntryResultDTO> getQueueStatus(String uuid) {
        logger.info("Queue status requested for token: " + uuid);
        QueueEntryResultDTO result = WebQueue.getInstance().getStatus(uuid);
        if (result.isAdmitted()) {
            logger.info("User with token " + uuid + " is now admitted");
        }
        return Response.ok(result);
    }

    // fired by WebQueue when a waiting user is admitted — uuid becomes their guest sessionId
    //triger for moving to login page //::TO DO!
    private void onUserAdmitted(String uuid) {
        logger.info("User admitted from queue with sessionId: " + uuid);
    }

    public Response<Boolean> registerUser(String activeIdentifier, UserDTO dto) {
        logger.info("Registration attempt started for email: " + dto.getEmail());
        try{
            if (auth.isLoggedIn(activeIdentifier).getValue()) {
                logger.warning("Registration failed: Active logged-in user attempted to register (Token: " + activeIdentifier + ")");
                return Response.error("Can't register to the system at member state, must be a guest.");
            }
            String email = dto.getEmail();
            if(email != null && userRepo.existsUser(email)){
                logger.warning("Registration failed: User with email " + email + " already exists");
                return Response.error("User " + email + " already exists");
            }
            LocalDate birthDate = null;
            try {
                 birthDate = LocalDate.of(dto.getYear(),dto.getMonth(),dto.getDay());
            } catch (DateTimeException e) {
                logger.warning("Registration failed for " + email + ": Invalid date format");
                return Response.error("Invalid format date");
            }
            if(birthDate.isAfter(LocalDate.now())){
                logger.warning("Registration failed for " + email + ": Future birth date provided");
                return Response.error("birth date cannot be after current date");
            }
            String mailRegex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
            Pattern emailPattern = Pattern.compile(mailRegex);
            Matcher emailMatcher = emailPattern.matcher(email);
            if(!emailMatcher.matches()){
                logger.warning("Registration failed: Invalid email format (" + email + ")");
                return Response.error("Invalid email format");
            }
            if(dto.getFirstName() == null || dto.getFirstName().isBlank()){
                logger.warning("Registration failed for " + email + ": First name is empty");
                return Response.error("First name cannot be empty");
            }
            if(dto.getLastName() == null || dto.getLastName().isBlank()){
                logger.warning("Registration failed for " + email + ": Last name is empty");
                return Response.error("Last name cannot be empty");
            }
            if(dto.getPassword()== null || dto.getPassword().isBlank()){
                logger.warning("Registration failed for " + email + ": Password is empty");
                return Response.error("Password cannot be empty");
            }
            if(dto.getAddress()== null || dto.getAddress().isBlank()){
                logger.warning("Registration failed for " + email + ": Address is empty");
                return Response.error("Address cannot be null");
            }
            String phoneRegex = "^[0-9]{3}-[0-9]{3}-[0-9]{4}$";
            Pattern phonePattern = Pattern.compile(phoneRegex);
            Matcher phoneMatcher = phonePattern.matcher(dto.getPhone());
            if(!phoneMatcher.matches()){
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
                    dto.getAddress());
            userRepo.store(member);
            logger.info("Registration successful for email: " + email);
            return Response.ok(true);
        }
        catch (Exception e){
            logger.severe("Registration failed due to unexpected server error for email " + dto.getEmail() + ". Error: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }

    public Response<String> login(String email, String password) {
        logger.info("Login attempt started for email: " + email);
        Response<String> tokenResponse = auth.login(email, password);
        if(tokenResponse.getValue() == null){
            logger.warning("Login attempt failed for " + email);
            return Response.error(tokenResponse.getMessage());
        }
        logger.info("Login successful for email: " + email);
        return new Response<>(tokenResponse.getValue(), tokenResponse.getMessage());
    }

    public Response<Boolean> logout(String token){
        logger.info("Logout attempt started for token: " + token);
        if(token == null || token.isBlank()) {
            logger.warning("Logout attempt failed: guest cannot log out");
            return new Response<>(false, "User is in guest state");
        }
        try {
            Response<Boolean> response = auth.logout(token);
            if(!response.getValue()){
                logger.warning("Logout attempt failed for " + token);
                return new Response<>(false, "Logout failed");
            }
            WebQueue.getInstance().notifyUserLeft();
            logger.info("Logout successful for token: " + token);
            return response;
        }
        catch (Exception e){
            logger.severe("Logout failed due to unexpected server error for token: " + token);
            return Response.error(e.getMessage());
        }
    }

    /**
     * System admin removes a member from the platform.
     *
     * Effects:
     *  - Member account is deactivated (isActive = false)
     *  - If the user is a founder of a company → that company is deactivated
     *  - If the user is an owner (non-founder) → removed from the company, their appointees cascade to founder
     *  - If the user is a manager → removed from the company
     *
     * TODO: add a proper admin-role check once the Admin concept is implemented.
     */
    public Response<Boolean> removeUser(String adminToken, int userIdToRemove) {
        logger.info("Admin removeUser attempt for userId: " + userIdToRemove);
        try {
            // 1. Requester must be logged in (admin check — TODO: verify admin role)
            if (!auth.isLoggedIn(adminToken).getValue()) {
                logger.warning("removeUser failed: requester is not logged in");
                return Response.error("Must be logged in to perform this action");
            }

            // 2. Find the member
            Member member = userRepo.findById(userIdToRemove);
            if (member == null) {
                logger.warning("removeUser failed: userId " + userIdToRemove + " not found");
                return Response.error("User not found");
            }
            if (!member.isActive()) {
                logger.warning("removeUser: userId " + userIdToRemove + " is already inactive");
                return Response.error("User is already removed");
            }

            // 3. Deactivate the account
            member.deactivate();

            // 4. Clean up all company roles
            for (Company company : companyRepo.getAll()) {
                boolean companyChanged = false;

                if (company.isFounder(userIdToRemove)) {
                    // Founder removed → deactivate the whole company
                    company.deactivate();
                    companyChanged = true;

                } else if (company.isOwner(userIdToRemove)) {
                    // Owner removed → cascade their appointees to the founder
                    company.forceRemoveOwner(userIdToRemove);
                    companyChanged = true;
                }

                if (company.getManagersPermissionsMap().containsKey(userIdToRemove)) {
                    // Manager removed → simply remove from the map
                    company.getManagersPermissionsMap().remove(userIdToRemove);
                    companyChanged = true;
                }

                if (companyChanged) {
                    companyRepo.store(company);
                }
            }

            // 5. Persist the deactivated member
            userRepo.store(member);

            logger.info("removeUser succeeded for userId: " + userIdToRemove);
            return Response.ok(true);

        } catch (Exception e) {
            logger.severe("removeUser failed for userId: " + userIdToRemove + ". Error: " + e.getMessage());
            return Response.error("Unexpected error: " + e.getMessage());
        }
    }
}
