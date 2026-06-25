package infrastructure;

import DTO.PaymentDetailsDTO;
import app.config.SystemProperties;
import application.IPaymentSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.logging.Logger;

@Component
@Profile("prod")
public class RealPaymentSystem implements IPaymentSystem {

    private static final Logger logger = Logger.getLogger(RealPaymentSystem.class.getName());
    private final String API_URL;
    private final RestTemplate restTemplate;

    @Autowired
    public RealPaymentSystem(RestTemplateBuilder restTemplateBuilder, SystemProperties systemProperties) {
        this.API_URL = systemProperties.getExternalApiUrl();
        int timeoutMinutes = systemProperties.getExternalApiTimeoutMinutes();

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMinutes(timeoutMinutes))
                .setReadTimeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }

    @Override
    public String pay(double total, PaymentDetailsDTO paymentDetails) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("action_type", "pay");
            map.add("amount", String.valueOf(total));
            map.add("currency", "NIS");
            map.add("card_number", paymentDetails.getCardNumber());
            map.add("month", paymentDetails.getMonth());
            map.add("year", paymentDetails.getYear());
            map.add("holder", paymentDetails.getCardHolderName());
            map.add("cvv", paymentDetails.getCvv());
            map.add("id", paymentDetails.getCardHolderId());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            String transactionId = response.getBody();

            if (transactionId == null || transactionId.trim().equals("-1")) {
                logger.warning("External payment system rejected the payment.");
                throw new RuntimeException("Payment rejected by the credit card company.");
            }

            return transactionId.trim();
        }catch (RestClientException e){
            logger.warning("External payment system rejected the payment.");
            throw new RuntimeException("Payment rejected by the credit card company.");
        } catch (RuntimeException e) {
                throw e;
        } catch (Exception e) {
            logger.severe("Payment failed due to communication error: " + e.getMessage());
            throw new RuntimeException("Payment gateway is currently unreachable. Please verify your details and try again later.");
        }
    }

    @Override
    public boolean refund(String paymentConfirmationId, double total) { //we just need the payment confirmationID
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("action_type", "refund");
            map.add("transaction_id", paymentConfirmationId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            String body = response.getBody();
            if (body != null && body.trim().equals("-1")) {
                logger.warning("External payment system explicitly rejected the refund (-1).");
                throw new RuntimeException("Refund explicitly rejected by the payment gateway.");
            }
            if (body == null || !body.trim().equals("1")) {
                logger.warning("External payment system returned unexpected response: " + body);
                throw new RuntimeException("Unexpected response from payment gateway during refund.");
            }
            return true;
        }catch (RestClientException e){
            logger.warning("External payment system rejected the payment.");
            throw new RuntimeException("Payment rejected by the payment gateway.");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.severe("Refund failed due to communication error: " + e.getMessage());
            throw new RuntimeException("Could not reach the payment gateway to process the refund. Please try again or contact support.");
        }
    }

    @Override
    public boolean handshake() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("action_type", "handshake");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            String body = response.getBody();
            return body != null && body.trim().equals("OK"); // [cite: 11]

        } catch (Exception e) {
            logger.severe("Handshake with payment system failed: " + e.getMessage());
            return false;
        }
    }
}