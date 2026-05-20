package application;

import DTO.NotifyDTO;

public interface INotifier {
    void notifyUser(String userIdentifier, NotifyDTO notification);

    boolean notifyTab(String tabId, NotifyDTO notification);
    void notifyMemberById(Integer userId, NotifyDTO payload);

    }
