package domain.dataType;

public enum CategoryEvent {
    LiveMusic,
    SPORTS,
    Theater,
    FESTIVAL,
    CONFERENCE,
    Other;

    public static CategoryEvent fromString(String value) {
        return CategoryEvent.valueOf(value.toUpperCase());
    }
}
