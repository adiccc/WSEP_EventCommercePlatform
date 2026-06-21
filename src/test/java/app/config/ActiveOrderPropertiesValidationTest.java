package app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveOrderPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties(ActiveOrderProperties.class)
    static class EnableProps {
    }

    @Test
    void GivenAllPropertiesPresentAndValid_WhenContextLoads_ThenSucceeds() {
        runner.withPropertyValues(
                "active-order.capacity=20",
                "active-order.selecting-timeout-minutes=5",
                "active-order.checkout-timeout-minutes=10",
                "active-order.warning-before-expiry-minutes=1"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ActiveOrderProperties.class).getCapacity()).isEqualTo(20);
        });
    }

    @Test
    void GivenMissingProperty_WhenContextLoads_ThenFailsFast() {
        runner.withPropertyValues(
                "active-order.capacity=20",
                "active-order.selecting-timeout-minutes=5",
                "active-order.checkout-timeout-minutes=10"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void GivenNonPositiveProperty_WhenContextLoads_ThenFailsFast() {
        runner.withPropertyValues(
                "active-order.capacity=0",
                "active-order.selecting-timeout-minutes=5",
                "active-order.checkout-timeout-minutes=10",
                "active-order.warning-before-expiry-minutes=1"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void GivenWarningNotLessThanCheckout_WhenContextLoads_ThenFailsFast() {
        runner.withPropertyValues(
                "active-order.capacity=20",
                "active-order.selecting-timeout-minutes=5",
                "active-order.checkout-timeout-minutes=10",
                "active-order.warning-before-expiry-minutes=10"
        ).run(context -> assertThat(context).hasFailed());
    }
}
