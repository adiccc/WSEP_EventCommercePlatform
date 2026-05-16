package domain.user;

import java.time.LocalDateTime;

public class Suspension {
    private int suspensionId;
    private int userId;
    private LocalDateTime startTime;
    private int duration; // in days
    private int version;

    public Suspension(int userId){
        this.startTime = LocalDateTime.now();
        this.userId = userId;
        this.suspensionId = -1; // would be updated when insert to repo
        duration=-1; // permanent suspension
        this.version=1;
    }
    public Suspension(int userId,int duration){
        this.userId=userId;
        this.suspensionId = -1;
        this.startTime = LocalDateTime.now();
        this.duration=duration; // temp suspension
        this.version=1;
    }

    public Suspension(Suspension suspension){
        this.suspensionId = suspension.suspensionId;
        this.userId = suspension.userId;
        this.startTime = suspension.startTime;
        this.duration = suspension.duration;
        this.version = suspension.version;
    }
    public int getSuspensionId() {
        return suspensionId;
    }
    public void setId(int suspensionId) {
        this.suspensionId = suspensionId;
    }
    public int getUserId() {
        return userId;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    public int getVersion(){
        return version;
    }

    public boolean isActive(){
        return suspensionId!=-1 || startTime.plusDays(duration).isAfter(LocalDateTime.now());
    }
}
