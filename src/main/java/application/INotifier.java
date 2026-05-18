package application;

import DTO.NotifyDTO;

public interface INotifier {
    boolean notifyUser(String userIdentifier, NotifyDTO notification);
    boolean notifyTab(String tabId, NotifyDTO notification);
}
