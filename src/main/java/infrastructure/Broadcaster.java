package infrastructure;

import com.vaadin.flow.shared.Registration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import DTO.NotifyDTO;
import org.springframework.stereotype.Component;

@Component
public class Broadcaster {

    private static final Executor executor = Executors.newSingleThreadExecutor();

    //for pop up notifications
    //key: userIdentifier (email or UUid), value: list of listeners for that user
    private static final Map<String, List<Consumer<NotifyDTO>>> userListeners = new ConcurrentHashMap<>();
    // for tab specific routing notifications
    //key: tabId, value: listener for that tab
    private static final Map<String, Consumer<NotifyDTO>> tabListeners = new ConcurrentHashMap<>();


    public static synchronized Registration registerUser(String userIdentifier, Consumer<NotifyDTO> popupListener) {
        userListeners.computeIfAbsent(userIdentifier, k -> new LinkedList<>()).add(popupListener);
        return () -> removeUserListener(userIdentifier, popupListener);
    }

    private static synchronized void removeUserListener(String userIdentifier, Consumer<NotifyDTO> popupListener) {
        List<Consumer<NotifyDTO>> listeners = userListeners.get(userIdentifier);
        if (listeners != null) {
            listeners.remove(popupListener);
            if (listeners.isEmpty()) {
                userListeners.remove(userIdentifier);
            }
            //TODO: in order to check if the user is logged in we need to check if the key is not exist in the map.

        }
    }

    public static boolean broadcastToUser(String userIdentifier, NotifyDTO notification) {
        List<Consumer<NotifyDTO>> listeners = userListeners.get(userIdentifier);
        if (listeners != null && !listeners.isEmpty()) {
            for (Consumer<NotifyDTO> listener : listeners) {
                executor.execute(() -> listener.accept(notification));
            }
            return true;
        }
        return false;
    }


    public static synchronized Registration registerTab(String tabId, Consumer<NotifyDTO> routingListener) {
        tabListeners.put(tabId, routingListener);
        return () -> removeTabListener(tabId);
    }

    private static synchronized void removeTabListener(String tabId) {
        tabListeners.remove(tabId);
        //TODO: in order to check if the user is logged in we need to check if the key is not exist in the map.
    }

    public static boolean broadcastToTab(String tabId, NotifyDTO notification) {
        Consumer<NotifyDTO> listener = tabListeners.get(tabId);
        if (listener != null) {
            executor.execute(() -> listener.accept(notification));
            return true;
        }
        return false;
    }
}