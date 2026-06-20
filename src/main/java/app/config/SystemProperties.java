package app.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "system")
@Validated
public class SystemProperties {

    @NotNull
    @Positive
    private Integer maxConcurrentUsers;

    @NotBlank
    private String initStateFile;

    @NotBlank
    private String accessCodeChars;

    @NotNull
    @Positive
    private Integer accessCodeLength;

    @Value("${jwt.expiration-time-hours:24}")
    private int tokenExpirationHours;

    public int getTokenExpirationHours() {
        return tokenExpirationHours;
    }

    public Integer getMaxConcurrentUsers() { return maxConcurrentUsers; }
    public void setMaxConcurrentUsers(Integer maxConcurrentUsers) { this.maxConcurrentUsers = maxConcurrentUsers; }

    public String getInitStateFile() { return initStateFile; }
    public void setInitStateFile(String initStateFile) { this.initStateFile = initStateFile; }

    public String getAccessCodeChars() { return accessCodeChars; }
    public void setAccessCodeChars(String accessCodeChars) { this.accessCodeChars = accessCodeChars; }

    public Integer getAccessCodeLength() { return accessCodeLength; }
    public void setAccessCodeLength(Integer accessCodeLength) { this.accessCodeLength = accessCodeLength; }
}
