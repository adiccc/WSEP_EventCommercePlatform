package infrastructure;

import DTO.NotifyDTO;
import application.INotifier;
import application.RetryHelper;
import domain.user.IUserRepo;
import domain.user.Member;
import Exception.OptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class VaadinNotifier implements INotifier {
    private static final Logger logger = Logger.getLogger(VaadinNotifier.class.getName());


    @Override
    public boolean notifyUser(String userIdentifier, NotifyDTO notification) {
        return Broadcaster.broadcastToUser(userIdentifier, notification);
    }

    @Override
    public boolean notifyTab(String tabId, NotifyDTO notification) {
        return Broadcaster.broadcastToTab(tabId, notification);
    }

}



