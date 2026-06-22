package infrastructure;

import DTO.PaymentDetailsDTO;
import app.config.SystemProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RealPaymentSystemTests {

    private RestTemplate restTemplate;
    private SystemProperties systemProperties;
    private RealPaymentSystem paymentSystem;
    private final String MOCK_URL = "https://mock-api.com/";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        systemProperties = mock(SystemProperties.class);

        when(systemProperties.getExternalApiUrl()).thenReturn(MOCK_URL);
        when(systemProperties.getExternalApiTimeoutMinutes()).thenReturn(2);

        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        paymentSystem = new RealPaymentSystem(builder, systemProperties);
    }


    @Test
    void GivenServerReturnsHtmlInsteadOfOk_WhenHandshake_ThenReturnsFalse() {
        ResponseEntity<String> fakeResponse = new ResponseEntity<>(
                "<html><body>502 Bad Gateway</body></html>",
                HttpStatus.OK
        );

        when(restTemplate.postForEntity(eq(MOCK_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(fakeResponse);

        boolean isUp = paymentSystem.handshake();
        assertFalse(isUp, "Handshake should fail when response is not exactly 'OK'");
    }

    @Test
    void GivenServerHangsAndTimesOut_WhenHandshake_ThenCatchesExceptionAndReturnsFalse() {
        when(restTemplate.postForEntity(eq(MOCK_URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Read timed out"));

        boolean isUp = paymentSystem.handshake();

        assertFalse(isUp, "Handshake should handle timeouts gracefully and return false");
    }

    @Test
    void GivenServerReturnsValidOk_WhenHandshake_ThenReturnsTrue() {
        ResponseEntity<String> validResponse = new ResponseEntity<>("OK", HttpStatus.OK);

        when(restTemplate.postForEntity(eq(MOCK_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(validResponse);

        boolean isUp = paymentSystem.handshake();

        assertTrue(isUp, "Handshake should return true when response is 'OK'");
    }
}