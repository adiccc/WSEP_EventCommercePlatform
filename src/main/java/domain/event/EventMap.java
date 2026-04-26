package domain.event;
import domain.dataType.ElementPosition;
import domain.event.Zone;
import domain.event.StandingZone;


import java.util.ArrayList;
import java.util.List;

public class EventMap {
    private List<Zone> zones;
    private ElementPosition stage;
    private List<ElementPosition> entries;


    public EventMap(ElementPosition stage, List<ElementPosition> entries, List<Zone> zones) {
        this.zones = zones;
        this.stage = stage;
        this.entries = entries;
    }
    public EventMap(EventMap eventMap) {
        this.zones=new ArrayList<>();
        for(Zone zone : eventMap.zones) {
            if(zone instanceof StandingZone)
                zones.add(new StandingZone((StandingZone)zone));
            if(zone instanceof SeatingZone)
                zones.add(new SeatingZone((SeatingZone)zone ));
        }
        this.entries=new ArrayList<>();
        for(ElementPosition entry : eventMap.getEntries()) {
            this.entries.add(new ElementPosition(entry));
        }
        this.stage=new ElementPosition(eventMap.getStage());
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
                if (((StandingZone) z).getCapacity() >= quantity) {
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