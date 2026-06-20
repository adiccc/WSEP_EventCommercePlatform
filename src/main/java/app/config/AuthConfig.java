package app.config;

import application.IAuth;
import application.TokenService;
import infrastructure.Auth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashSet;

@Configuration
public class AuthConfig {

    @Bean
    @Primary
    public IAuth auth(TokenService tokenService, SystemProperties systemProperties) {
        return new Auth(tokenService, new HashSet<>(systemProperties.getAdminEmails()));
    }
}
