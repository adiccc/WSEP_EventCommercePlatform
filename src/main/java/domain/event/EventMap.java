package domain.event;
import domain.dataType.ElementPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        this.zones = eventMap.zones;
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

//    public List<Integer> bookStandingTickets(int userid,String zone, int quantity) {
//        //find the zone
//        for (Zone z : zones) {
//            if (z.getName().equals(zone) && z instanceof StandingZone) {
//                //check if there are enough tickets available
//                if (((StandingZone) z).getAvaliable() >= quantity) {
//                    //book the tickets and return their IDs
//                    return ((StandingZone) z).bookTickets(userid,quantity);
//                } else {
//                    throw new IllegalArgumentException("Not enough tickets available in this zone.");
//                }
//            }
//        }
//        throw new IllegalArgumentException("Zone not found.");
//    }

    public List<Integer> bookTickets(Map<String,List<String>> seatingZones,Map<String,Integer>standingZones){
        List<Integer> bookedTicketsIds = new ArrayList<>();
        //book seating zones
        for (Map.Entry<String, List<String>> entry : seatingZones.entrySet()) {
            String zoneName = entry.getKey();
            List<String> seats = entry.getValue();
            for (Zone z : zones) {
                if (z.getName().equals(zoneName) && z instanceof SeatingZone) {
                    bookedTicketsIds.addAll(((SeatingZone) z).bookTickets(seats));
                }
            }
        }
        //book standing zones
        for (Map.Entry<String, Integer> entry : standingZones.entrySet()) {
            String zoneName = entry.getKey();
            int quantity = entry.getValue();
            for (Zone z : zones) {
                if (z.getName().equals(zoneName) && z instanceof StandingZone) {
                    bookedTicketsIds.addAll(((StandingZone) z).bookTickets(quantity));
                }
            }
        }
        return bookedTicketsIds;


    }
}