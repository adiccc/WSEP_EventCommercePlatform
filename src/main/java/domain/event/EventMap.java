package domain.event;
import domain.dataType.ElementPosition;

import java.util.List;

public class EventMap {
    private final List<Zone> zones;
    private final ElementPosition stage;
    private final List<ElementPosition> entries;


    public EventMap(ElementPosition stage, List<ElementPosition> entries, List<Zone> zones) {
        this.zones = zones;
        this.stage = stage;
        this.entries = entries;
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

    public List<Integer> bookStandingTickets(int userid,String zone, int quantity) {
        //find the zone
        for (Zone z : zones) {
            if (z.getName().equals(zone) && z instanceof StandingZone) {
                //check if there are enough tickets available
                if (((StandingZone) z).getAvaliable() >= quantity) {
                    //book the tickets and return their IDs
                    return ((StandingZone) z).bookTickets(userid,quantity);
                } else {
                    throw new IllegalArgumentException("Not enough tickets available in this zone.");
                }
            }
        }
        throw new IllegalArgumentException("Zone not found.");
    }
}