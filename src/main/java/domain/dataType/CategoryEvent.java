package domain.dataType;

public enum CategoryEvent {
    Live,
    Sport,
    Theater,
    Festival,
    Conference,
    Other;

    public static CategoryEvent fromString(String value) {
        return CategoryEvent.valueOf(value.toUpperCase());
    }
}
