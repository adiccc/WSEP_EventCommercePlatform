package app.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "active-order")
@Validated
public class ActiveOrderProperties {

    @NotNull
    @Positive
    private Integer capacity;

    @NotNull
    @Positive
    private Integer selectingTimeoutMinutes;

    @NotNull
    @Positive
    private Integer checkoutTimeoutMinutes;

    @NotNull
    @Positive
    private Integer warningBeforeExpiryMinutes;

    @AssertTrue(message = "active-order.warning-before-expiry-minutes must be less than active-order.checkout-timeout-minutes")
    public boolean isWarningWindowValid() {
        return warningBeforeExpiryMinutes != null
                && checkoutTimeoutMinutes != null
                && warningBeforeExpiryMinutes < checkoutTimeoutMinutes;
    }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getSelectingTimeoutMinutes() { return selectingTimeoutMinutes; }
    public void setSelectingTimeoutMinutes(Integer selectingTimeoutMinutes) { this.selectingTimeoutMinutes = selectingTimeoutMinutes; }

    public Integer getCheckoutTimeoutMinutes() { return checkoutTimeoutMinutes; }
    public void setCheckoutTimeoutMinutes(Integer checkoutTimeoutMinutes) { this.checkoutTimeoutMinutes = checkoutTimeoutMinutes; }

    public Integer getWarningBeforeExpiryMinutes() { return warningBeforeExpiryMinutes; }
    public void setWarningBeforeExpiryMinutes(Integer warningBeforeExpiryMinutes) { this.warningBeforeExpiryMinutes = warningBeforeExpiryMinutes; }
}
