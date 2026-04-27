package domain.event;
import DTO.StandingZoneDTO;
import domain.dataType.ElementPosition;
import domain.dataType.SeatingZone;
import domain.dataType.StandingZone;
import domain.dataType.Zone;

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

}