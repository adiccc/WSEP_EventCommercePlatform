package domain.user;

import java.time.LocalDateTime;

public class Guest extends User {
    public Guest(String sessionId){
        super(sessionId);
    }
}