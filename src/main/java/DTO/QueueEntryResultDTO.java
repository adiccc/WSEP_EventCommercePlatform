package DTO;

public class QueueEntryResultDTO {
    private final String token;
    private final boolean admitted;
    private final int position; // -1 when admitted

    public QueueEntryResultDTO(String token, boolean admitted, int position) {
        this.token = token;
        this.admitted = admitted;
        this.position = position;
    }

    public String getToken() { return token; }
    public boolean isAdmitted() { return admitted; }
    public int getPosition() { return position; }
}
