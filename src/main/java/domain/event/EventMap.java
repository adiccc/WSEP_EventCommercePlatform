package domain.event;
import domain.dataType.ElementPosition;
import domain.dataType.Zone;

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
}