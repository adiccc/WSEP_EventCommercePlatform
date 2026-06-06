package domain.Suspension;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "suspensions")
public class Suspension {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suspension_id")
    private Long suspensionId;
    @Column(name = "user_id", nullable = false)
    private int userId;
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    @Column(name = "end_time")
    private LocalDateTime endTime;
    @Version
    private int version;

    public Suspension(int userId){
        this.startTime = LocalDateTime.now();
        this.userId = userId;
        this.suspensionId = null; // would be updated when insert to repo
        endTime=null; // permanent suspension
        this.version=1;
    }
    public Suspension(int userId,long duration){
        this.userId=userId;
        this.suspensionId = null;
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

    // jpa use
    public Suspension() {
    }

    public void unsuspend(){
        this.endTime= LocalDateTime.now().minusMinutes(1);
    }
    public Long getSuspensionId() {
        return suspensionId;
    }
    public void setId(Long suspensionId) {
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
        return endTime == null || endTime.isAfter(LocalDateTime.now());
    }

    public LocalDateTime getEndDate(){
        return endTime;
    }
    public LocalDateTime getStartDate(){
        return startTime;
    }
}
