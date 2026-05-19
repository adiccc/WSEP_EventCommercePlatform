package infrastructure;

import DTO.NotifyDTO;
import application.INotifier;
import domain.user.IUserRepo;
import domain.user.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class VaadinNotifier implements INotifier {
    private static final Logger logger = Logger.getLogger(VaadinNotifier.class.getName());
    private final IUserRepo userRepo;
    @Autowired
    public VaadinNotifier(IUserRepo userRepo) {
        this.userRepo = userRepo;
    }
    @Override
    public void notifyUser(String userIdentifier, NotifyDTO notification){
        logger.info("Attempting to send real-time notification to user: " + userIdentifier);
        try {
            boolean isSentRealTime = Broadcaster.broadcastToUser(userIdentifier, notification);

            if (!isSentRealTime) {
                logger.info("User " + userIdentifier + " is offline. Saving as delayed notification.");
                Member member = userRepo.findUserByEmail(userIdentifier);

                if (member != null) {
                    member.addDelayedNotification(notification);
                    userRepo.store(member);
                    logger.info("Delayed notification saved successfully for: " + userIdentifier);
                } else {
                    logger.warning("Failed to save delayed notification: Member not found for email " + userIdentifier);
                }
            }
        } catch (Exception e) {
            logger.severe("Error in notifyUser for " + userIdentifier + ": " + e.getMessage());
        }
    }
    @Override
    public boolean notifyTab(String tabId, NotifyDTO notification){ //because we dont have suspended notifications
        return Broadcaster.broadcastToTab(tabId, notification);
    }
    @Override
    public void deliverDelayedNotifications(String userIdentifier) {
        logger.info("Fetching delayed notifications for user: " + userIdentifier);
        try {
            Member member = userRepo.findUserByEmail(userIdentifier);
            if (member != null && !member.getDelayedNotifications().isEmpty()) {
                List<NotifyDTO> pendingNotifications = new ArrayList<>(member.getDelayedNotifications());
                logger.info("Found " + pendingNotifications.size() + " delayed notifications for " + userIdentifier);
                for (NotifyDTO notification : pendingNotifications) {
                    Broadcaster.broadcastToUser(userIdentifier, notification);
                }
                member.clearDelayedNotifications();
                userRepo.store(member);
                logger.info("Successfully delivered and cleared delayed notifications for: " + userIdentifier);
            }
        } catch (Exception e) {
            logger.severe("Error delivering delayed notifications for " + userIdentifier + ": " + e.getMessage());
        }
    }
}



