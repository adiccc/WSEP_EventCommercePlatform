package application;

import DTO.NotifyDTO;

public interface INotifier {
    void notifyUser(String userIdentifier, NotifyDTO notification);
    boolean notifyTab(String tabId, NotifyDTO notification);
    void deliverDelayedNotifications(String userIdentifier);
}
