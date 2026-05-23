package UI;


import domain.webQueue.WebQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebQueueConfig {

    @Bean
    public WebQueue webQueue() {
        return WebQueue.getInstance(1);
    }
}
