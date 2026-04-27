package application;

import domain.company.Company;
import domain.company.ICompanyRepo;
import domain.company.Permissions;
import domain.user.IUserRepo;
import domain.user.Member;
import domain.webQueue.WebQueue;

import java.util.logging.Logger;

public class AdminService {
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IAuth auth;
    private final IUserRepo userRepo;
    private final ICompanyRepo companyRepo;

    public AdminService(IAuth auth, IUserRepo userRepo, ICompanyRepo companyRepo) {
        this.auth = auth;
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
    }

    public Response<Boolean> setMaxCapacity(String token, int capacity) {
        logger.info("setMaxCapacity attempt");
        try {
            if (!auth.isAdmin(token).getValue()) {
                logger.warning("setMaxCapacity failed: unauthorized");
                return Response.error("Unauthorized: admin access required");
            }
            WebQueue.getInstance().setMaxCapacity(capacity);
            logger.info("Max capacity updated to: " + capacity);
            return Response.ok(true);
        } catch (Exception e) {
            logger.severe("setMaxCapacity failed due to server error: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }

    public Response<Integer> getMaxCapacity(String token) {
        logger.info("getMaxCapacity attempt");
        try {
            if (!auth.isAdmin(token).getValue()) {
                logger.warning("getMaxCapacity failed: unauthorized");
                return Response.error("Unauthorized: admin access required");
            }
            return Response.ok(WebQueue.getInstance().getMaxCapacity());
        } catch (Exception e) {
            logger.severe("getMaxCapacity failed due to server error: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }

    public Response<Integer> getActiveCount(String token) {
        logger.info("getActiveCount attempt");
        try {
            if (!auth.isAdmin(token).getValue()) {
                logger.warning("getActiveCount failed: unauthorized");
                return Response.error("Unauthorized: admin access required");
            }
            return Response.ok(WebQueue.getInstance().getActiveCount());
        } catch (Exception e) {
            logger.severe("getActiveCount failed due to server error: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }

    /**
     * Removes a member from the platform entirely.
     *
     * Effects:
     *  - Member account is deactivated (isActive = false)
     *  - If the user is the founder of a company → that company is deactivated
     *  - If the user is an owner (non-founder) → removed from ownerIds;
     *    all managers they appointed cascade to the founder
     *  - If the user is a manager → removed from companyTree;
     *    all sub-managers they appointed cascade to the founder
     */
    public Response<Boolean> removeUser(String adminToken, int userIdToRemove) {
        logger.info("removeUser attempt for userId: " + userIdToRemove);
        try {
            if (!auth.isAdmin(adminToken).getValue()) {
                logger.warning("removeUser failed: unauthorized");
                return Response.error("Unauthorized: admin access required");
            }

            Member member = userRepo.findById(userIdToRemove);
            if (member == null) {
                logger.warning("removeUser failed: userId " + userIdToRemove + " not found");
                return Response.error("User not found");
            }
            if (!member.isActive()) {
                logger.warning("removeUser: userId " + userIdToRemove + " already inactive");
                return Response.error("User is already removed");
            }

            for (Company company : companyRepo.getAll()) {
                Permissions perms = company.getCompanyPermission();
                boolean changed = false;

                if (perms.getFounderId() == userIdToRemove) {
                    // Founder removed → deactivate the whole company
                    company.deactivate();
                    changed = true;
                } else if (perms.isOwner(userIdToRemove)) {
                    // Owner removed → cascade their manager appointees to founder
                    perms.removeOwnerAndCascade(userIdToRemove);
                    changed = true;
                }

                if (perms.getCompanyTree().containsKey(userIdToRemove)) {
                    // Manager removed → cascade their sub-managers to founder
                    perms.removeManagerAndCascade(userIdToRemove);
                    changed = true;
                }

                if (changed) companyRepo.store(company);
            }

            member.deactivate();
            userRepo.store(member);

            logger.info("removeUser succeeded for userId: " + userIdToRemove);
            return Response.ok(true);

        } catch (Exception e) {
            logger.severe("removeUser failed for userId: " + userIdToRemove + ". Error: " + e.getMessage());
            return Response.error("Unexpected error: " + e.getMessage());
        }
    }

    public Response<Integer> getWaitingCount(String token) {
        logger.info("getWaitingCount attempt");
        try {
            if (!auth.isAdmin(token).getValue()) {
                logger.warning("getWaitingCount failed: unauthorized");
                return Response.error("Unauthorized: admin access required");
            }
            return Response.ok(WebQueue.getInstance().getWaitingCount());
        } catch (Exception e) {
            logger.severe("getWaitingCount failed due to server error: " + e.getMessage());
            return Response.error(e.getMessage());
        }
    }
}
