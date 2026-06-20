package app.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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

    @NotNull
    @Positive
    private Integer tokenExpirationHours;

    public Integer getTokenExpirationHours() {
        return tokenExpirationHours;
    }

    public void setTokenExpirationHours(Integer tokenExpirationHours) {
        this.tokenExpirationHours = tokenExpirationHours;
    }

    @NotEmpty
    private List<String> adminEmails;

    public Integer getMaxConcurrentUsers() { return maxConcurrentUsers; }
    public void setMaxConcurrentUsers(Integer maxConcurrentUsers) { this.maxConcurrentUsers = maxConcurrentUsers; }

    public String getInitStateFile() { return initStateFile; }
    public void setInitStateFile(String initStateFile) { this.initStateFile = initStateFile; }

    public String getAccessCodeChars() { return accessCodeChars; }
    public void setAccessCodeChars(String accessCodeChars) { this.accessCodeChars = accessCodeChars; }

    public Integer getAccessCodeLength() { return accessCodeLength; }
    public void setAccessCodeLength(Integer accessCodeLength) { this.accessCodeLength = accessCodeLength; }

    public List<String> getAdminEmails() { return adminEmails; }
    public void setAdminEmails(List<String> adminEmails) { this.adminEmails = adminEmails; }
}
