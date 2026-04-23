package domain.lottery;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Lottery {
    private int id;
    private int eventId;
    private int capacity;
    private List<Integer> registered;
    private List<Integer> winners; //the code of each user who won the lottery is his userId/email
    private Date registerWindow; // the time window for users to register for the lottery
    private double expirationTime; // after this time all the users will be able to buy tickets for the event, and the lottery will be closed

    public Lottery(int id, int eventId, int capacity, Date registerWindow, double expirationTime) {
        this.id = id;
        this.eventId = eventId;
        this.capacity = capacity;
        this.registered = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.registerWindow = registerWindow;
        this.expirationTime = expirationTime;
    }
}