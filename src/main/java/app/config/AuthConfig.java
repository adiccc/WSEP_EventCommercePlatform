package app.config;

import application.IAuth;
import application.TokenService;
import infrastructure.Auth;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Set;

@Configuration
public class AuthConfig {

    @Bean
    @Primary
    public IAuth auth(TokenService tokenService) {
        return new Auth(
                tokenService,
                Set.of("systemadmin@demo.com")
        );
    }
}
