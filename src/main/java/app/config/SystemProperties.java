package app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "system")
public class SystemProperties {

    private int maxConcurrentUsers;
    private int activeOrderTtlMinutes;
    private String initStateFile;

    public int getMaxConcurrentUsers() { return maxConcurrentUsers; }
    public void setMaxConcurrentUsers(int maxConcurrentUsers) { this.maxConcurrentUsers = maxConcurrentUsers; }

    public int getActiveOrderTtlMinutes() { return activeOrderTtlMinutes; }
    public void setActiveOrderTtlMinutes(int activeOrderTtlMinutes) { this.activeOrderTtlMinutes = activeOrderTtlMinutes; }

    public String getInitStateFile() { return initStateFile; }
    public void setInitStateFile(String initStateFile) { this.initStateFile = initStateFile; }
}
