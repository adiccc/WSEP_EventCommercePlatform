package domain.dataType;

public enum GeographicalArea {
    CENTER,
    JERUSALEM,
    NORTH,
    SOUTH,;

    public static GeographicalArea fromString(String value) {
        return GeographicalArea.valueOf(value.toUpperCase());
    }
}
