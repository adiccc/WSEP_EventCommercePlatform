package application;

import domain.webQueue.WebQueue;

import java.util.logging.Logger;

public class AdminService {
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IAuth auth;

    public AdminService(IAuth auth) {
        this.auth = auth;
    }

    public Response<Boolean> setMaxCapacity(String token, int capacity) {
        return RetryHelper.executeWithRetry(() ->
        {
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
        });
    }

    public Response<Integer> getMaxCapacity(String token) {
        return RetryHelper.executeWithRetry(() ->
        {
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
        });
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

    public Response<Integer> getWaitingCount(String token) {
        return RetryHelper.executeWithRetry(() ->
        {
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
        });
    }
}
