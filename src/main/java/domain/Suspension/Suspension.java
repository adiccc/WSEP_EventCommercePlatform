package domain.Suspension;

import java.time.LocalDateTime;

public class Suspension {
    private int suspensionId;
    private int userId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int version;

    public Suspension(int userId){
        this.startTime = LocalDateTime.now();
        this.userId = userId;
        this.suspensionId = -1; // would be updated when insert to repo
        endTime=null; // permanent suspension
        this.version=1;
    }
    public Suspension(int userId,long duration){
        this.userId=userId;
        this.suspensionId = -1;
        this.startTime = LocalDateTime.now();
        this.endTime=startTime.plusDays(duration); // temp suspension
        this.version=1;
    }

    public Suspension(Suspension suspension){
        this.suspensionId = suspension.suspensionId;
        this.userId = suspension.userId;
        this.startTime = suspension.startTime;
        this.endTime = suspension.endTime;
        this.version = suspension.version;
    }
    public void unsuspend(){
        this.endTime= LocalDateTime.now().minusMinutes(1);
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

    public boolean isActive() {
        return suspensionId != -1 && (endTime == null || endTime.isAfter(LocalDateTime.now()));
    }

    public LocalDateTime getEndDate(){
        return endTime;
    }
    public LocalDateTime getStartDate(){
        return startTime;
    }
}
