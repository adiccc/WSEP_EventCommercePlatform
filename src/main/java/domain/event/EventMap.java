package domain.event;
import domain.dataType.ElementPosition;
import domain.dto.SeatingTicketDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class EventMap {
    private final List<Zone> zones;
    private final ElementPosition stage;
    private final List<ElementPosition> entries;


    public EventMap(ElementPosition stage, List<ElementPosition> entries, List<Zone> zones) {
        this.zones = zones;
        this.stage = stage;
        this.entries = entries;
    }

    public EventMap(EventMap eventMap) {
        this.zones = new ArrayList<>();
        for (Zone z : eventMap.zones) {
            if (z instanceof SeatingZone seatingZone) {
                this.zones.add(new SeatingZone(seatingZone, new AtomicInteger(seatingZone.getTicketIdGenerator().get())));
            } else if (z instanceof StandingZone standingZone) {
                this.zones.add(new StandingZone(standingZone));
            }
        }
        this.stage = eventMap.stage;
        this.entries = eventMap.entries;
    }

    public List<Zone> getZones() {
        return zones;
    }

    public ElementPosition getStage() {
        return stage;
    }

    public List<ElementPosition> getEntries() {
        return entries;
    }


    public List<Integer> bookTickets(Map<String, List<SeatingTicketDTO>> seatingZones,
                                     Map<String, Integer> standingZones) {

        List<Integer> bookedTicketsIds = new ArrayList<>();

        // --- Seating zones ---
        for (Map.Entry<String, List<SeatingTicketDTO>> entry : seatingZones.entrySet()) {
            String zoneName = entry.getKey();
            List<SeatingTicketDTO> seats = entry.getValue();
            Zone matchedZone = null;
            for (Zone z : zones) {
                if (z.getName().equals(zoneName)) {
                    matchedZone = z;
                    break;
                }
            }
            if (matchedZone == null) {
                throw new IllegalArgumentException("Seating zone does not exist: " + zoneName);
            }
            if (!(matchedZone instanceof SeatingZone)) {
                throw new IllegalArgumentException("Zone is not a seating zone: " + zoneName);
            }
            bookedTicketsIds.addAll(((SeatingZone) matchedZone).bookTickets(seats));
        }
        // --- Standing zones ---
        for (Map.Entry<String, Integer> entry : standingZones.entrySet()) {
            String zoneName = entry.getKey();
            int quantity = entry.getValue();
            Zone matchedZone = null;
            for (Zone z : zones) {
                if (z.getName().equals(zoneName)) {
                    matchedZone = z;
                    break;
                }
            }
            if (matchedZone == null) {
                throw new IllegalArgumentException("Standing zone does not exist: " + zoneName);
            }
            if (!(matchedZone instanceof StandingZone)) {
                throw new IllegalArgumentException("Zone is not a standing zone: " + zoneName);
            }
            bookedTicketsIds.addAll(((StandingZone) matchedZone).bookTickets(quantity));
        }
        return bookedTicketsIds;
    }

    public void releaseTickets(List<Integer> ticketIds) {
        for (Zone z : zones) {
            z.releaseTickets(ticketIds);
        }
    }

    public int countStandingInZone(String zone, List<Integer> currentTickets) {
        for (Zone z : zones) {
            if (z.getName().equals(zone)) {
                if (z instanceof StandingZone) {
                    return ((StandingZone) z).countTickets(currentTickets);
                } else {
                    throw new IllegalArgumentException("Zone is not a standing zone: " + zone);
                }
            }
        }
        throw new IllegalArgumentException("Zone does not exist: " + zone);
    }
}