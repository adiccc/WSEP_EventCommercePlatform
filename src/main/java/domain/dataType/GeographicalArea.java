package domain.dataType;

public enum GeographicalArea {
    Center,
    Jerusalem,
    North,
    South;

    public static GeographicalArea fromString(String value) {
        return GeographicalArea.valueOf(value.toUpperCase());
    }
}
