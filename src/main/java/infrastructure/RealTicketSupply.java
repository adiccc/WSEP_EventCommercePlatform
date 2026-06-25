package infrastructure;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;
import DTO.PurchasedTicketDTO;
import app.config.SystemProperties;
import application.ITicketSupply;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Component
@Profile("prod")
public class RealTicketSupply implements ITicketSupply {

    private static final Logger logger = Logger.getLogger(RealTicketSupply.class.getName());
    private final String API_URL;
    private final RestTemplate restTemplate;

    @Autowired
    public RealTicketSupply(RestTemplateBuilder restTemplateBuilder, SystemProperties systemProperties) {
        this.API_URL = systemProperties.getExternalApiUrl();
        int timeoutMinutes = systemProperties.getExternalApiTimeoutMinutes();

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMinutes(timeoutMinutes))
                .setReadTimeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }

    @Override
    public TicketSupplyResultDTO issue(TicketSupplyRequestDTO request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("action_type", "issue_ticket");
            map.add("customer_id", request.getCustomerId());
            map.add("event_id", request.getEventId());
            map.add("zone", request.getZoneName());

            int quantityRequested = 0;

            if (request.isSeating()) {
                map.add("is_seating", "true");
                List<PurchasedTicketDTO> purchasedTickets = request.getPurchasedTickets();

                if (purchasedTickets != null && !purchasedTickets.isEmpty()) {
                    quantityRequested = purchasedTickets.size();
                    String seatsArray = purchasedTickets.stream()
                            .map(t -> String.format("{\"row\": %d, \"seat\": %d}", t.getRow(), t.getCol()))
                            .collect(Collectors.joining(", "));
                    map.add("seats", "[" + seatsArray + "]");
                }
            } else {
                List<PurchasedTicketDTO> purchasedTickets = request.getPurchasedTickets();
                quantityRequested = (purchasedTickets != null && !purchasedTickets.isEmpty())
                        ? purchasedTickets.size()
                        : request.getTickets().size();
                map.add("quantity", String.valueOf(quantityRequested));
            }

            HttpEntity<MultiValueMap<String, String>> httpRequest = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, httpRequest, String.class);

            String ticketCode = response.getBody();

            if (ticketCode == null || ticketCode.trim().equals("-1") || ticketCode.trim().isEmpty()) {
                logger.warning("External ticket system returned failure or empty code");
                throw new RuntimeException("Ticket issuance was rejected by the external provider.");
            }
            List<String> duplicatedCodes = java.util.Collections.nCopies(quantityRequested, ticketCode.trim());
            return new TicketSupplyResultDTO(true, duplicatedCodes);
        } catch (RestClientException e) {
            logger.warning("External ticket system returned failure");
            throw new RuntimeException("External ticket system returned failure");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.severe("Ticket issue request failed: " + e.getMessage());
            throw new RuntimeException("We are experiencing temporary issues with the ticket provider. Please try again in a few moments.");        }
    }
    @Override
    public boolean cancelTicket(String ticketCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("action_type", "cancel_ticket");
            map.add("ticket_id", ticketCode);

            HttpEntity<MultiValueMap<String, String>> httpRequest = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, httpRequest, String.class);

            String body = response.getBody();

            if (body != null && body.trim().equals("-1")) {
                logger.warning("External ticket supply system explicitly rejected canceling ticket (-1).");
                throw new RuntimeException("Cancel ticket explicitly rejected by the ticket supply gateway.");
            }
            if (body == null || !body.trim().equals("1")) {
                logger.warning("External ticket system failed to cancel ticket.");
                throw new RuntimeException("Failed to cancel ticket in the external system.");
            }

            return true;
        } catch (RestClientException e) {
            logger.warning("External ticket system returned failure");
            throw new RuntimeException("External ticket system returned failure");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.severe("Ticket cancellation failed due to communication error: " + e.getMessage());
            throw new RuntimeException("Could not reach the external ticket system to cancel tickets. Please try again.");
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
            return body != null && body.trim().equals("OK");

        } catch (Exception e) {
            logger.severe("Handshake failed: External system is unreachable - " + e.getMessage());
            return false;
        }
    }
    }
            