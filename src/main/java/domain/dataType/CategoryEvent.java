package domain.dataType;

public enum CategoryEvent {
    LIVEMUSIC,
    SPORTS,
    THEATER,
    FESTIVAL,
    CONFERENCE,
    OTHER;

    public static CategoryEvent fromString(String value) {
        return CategoryEvent.valueOf(value.toUpperCase());
    }
}
