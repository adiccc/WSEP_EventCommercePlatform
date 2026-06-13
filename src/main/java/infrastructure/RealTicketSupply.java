package infrastructure;

import DTO.TicketSupplyRequestDTO;
import DTO.TicketSupplyResultDTO;
import DTO.PurchasedTicketDTO;
import application.ITicketSupply;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Component
@Profile("prod")
public class RealTicketSupply implements ITicketSupply {

    private static final Logger logger = Logger.getLogger(RealTicketSupply.class.getName());
    private static final String API_URL = "https://damp-lynna-wsep-1984852e.koyeb.app/";
    private final RestTemplate restTemplate = new RestTemplate();

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

            if (request.isSeating()) {
                map.add("is_seating", "true");

                String seatsJson = "[]";
                List<PurchasedTicketDTO> purchasedTickets = request.getPurchasedTickets();
                if (purchasedTickets != null && !purchasedTickets.isEmpty()) {
                    String seatsArray = purchasedTickets.stream()
                            .map(t -> String.format("{\"row\": %d, \"seat\": %d}", t.getRow(), t.getCol()))
                            .collect(Collectors.joining(", "));
                    seatsJson = "[" + seatsArray + "]";
                }
                map.add("seats", seatsJson);

            } else {
                List<PurchasedTicketDTO> purchasedTickets = request.getPurchasedTickets();
                int quantity = (purchasedTickets != null && !purchasedTickets.isEmpty())
                        ? purchasedTickets.size()
                        : request.getTickets().size();
                map.add("quantity", String.valueOf(quantity));
            }

            HttpEntity<MultiValueMap<String, String>> httpRequest = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, httpRequest, String.class);

            String ticketCode = response.getBody();

            if (ticketCode == null || ticketCode.trim().equals("1")) {
                logger.warning("External ticket system returned failure (1)");
                return new TicketSupplyResultDTO(false, List.of());
            }

            return new TicketSupplyResultDTO(true, List.of(ticketCode.trim()));

        } catch (Exception e) {
            logger.severe("Ticket issue request failed due to communication error: " + e.getMessage());
            return new TicketSupplyResultDTO(false, List.of());
        }
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
            return body != null && body.trim().equals("1");

        } catch (Exception e) {
            logger.severe("Ticket cancellation failed due to communication error: " + e.getMessage());
            return false;
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