package infrastructure;

import DTO.NotifyDTO;
import application.INotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VaadinNotifier implements INotifier {

    @Override
    public boolean notifyUser(String userIdentifier, NotifyDTO notification){
        return Broadcaster.broadcastToUser(userIdentifier, notification);
    }

    @Override
    public boolean notifyTab(String tabId, NotifyDTO notification){
        return Broadcaster.broadcastToTab(tabId, notification);
    }
}



