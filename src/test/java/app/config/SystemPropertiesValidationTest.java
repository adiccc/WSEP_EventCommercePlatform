package app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPropertiesValidationTest {

    private static final String[] VALID_PROPS = {
            "system.max-concurrent-users=50",
            "system.init-state-file=classpath:init-state.json",
            "system.access-code-chars=ABCDE",
            "system.access-code-length=6",
            "system.admin-emails=admin@test.com",
            "system.token-expiration-hours=24",

            "external-api-url=https://damp-lynna-wsep-1984852e.koyeb.app/",
            "external-api-timeout-minutes=10"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties(SystemProperties.class)
    static class EnableProps {}

//    @Test
//    void GivenAllValidProperties_WhenContextLoads_ThenSucceeds() {
//        runner.withPropertyValues(VALID_PROPS)
//                .run(ctx -> assertThat(ctx).hasNotFailed());
//    }

    @Test
    void GivenMissingAdminEmails_WhenContextLoads_ThenFails() {
        runner.withPropertyValues(
                "system.max-concurrent-users=50",
                "system.init-state-file=classpath:init-state.json",
                "system.access-code-chars=ABCDE",
                "system.access-code-length=6",
                "system.token-expiration-hours=24",
                "external-api-url=https://damp-lynna-wsep-1984852e.koyeb.app/",
                "external-api-timeout-minutes=10"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void GivenNonPositiveMaxConcurrentUsers_WhenContextLoads_ThenFails() {
        runner.withPropertyValues(VALID_PROPS)
                .withPropertyValues("system.max-concurrent-users=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void GivenMissingInitStateFile_WhenContextLoads_ThenFails() {
        runner.withPropertyValues(
                "system.max-concurrent-users=50",
                "system.access-code-chars=ABCDE",
                "system.access-code-length=6",
                "system.admin-emails=admin@test.com",
                "system.token-expiration-hours=24",
                "external-api-url=https://damp-lynna-wsep-1984852e.koyeb.app/",
                "external-api-timeout-minutes=10"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void GivenMissingAccessCodeChars_WhenContextLoads_ThenFails() {
        runner.withPropertyValues(
                "system.max-concurrent-users=50",
                "system.init-state-file=classpath:init-state.json",
                "system.access-code-length=6",
                "system.admin-emails=admin@test.com",
                "system.token-expiration-hours=24",
                "external-api-url=https://damp-lynna-wsep-1984852e.koyeb.app/",
                "external-api-timeout-minutes=10"
        ).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void GivenNonPositiveAccessCodeLength_WhenContextLoads_ThenFails() {
        runner.withPropertyValues(VALID_PROPS)
                .withPropertyValues("system.access-code-length=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void GivenDefaultConfigYml_WhenContextLoads_ThenSucceeds() {
        runner.withInitializer(ctx -> {
            try {
                var loader = new YamlPropertySourceLoader();
                var resource = new ClassPathResource("config.yml");
                var sources = loader.load("config", resource);
                sources.forEach(s -> ctx.getEnvironment().getPropertySources().addLast(s));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config.yml", e);
            }
        }).run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
