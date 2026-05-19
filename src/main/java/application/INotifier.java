package application;

import DTO.NotifyDTO;

public interface INotifier {
    void notifyUser(String userIdentifier, NotifyDTO notification);
    boolean notifyTab(String tabId, NotifyDTO notification);
    boolean deliverDelayedNotifications(String userIdentifier);
    void notifyMemberById(Integer userId, NotifyDTO payload);
}
