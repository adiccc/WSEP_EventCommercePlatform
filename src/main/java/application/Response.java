package application;

public class Response  <T>  {
    private T value;
    private String message;

    public Response(T value, String message) {
        this.value = value;
        this.message = message;
    }
    public T getValue() {
        return value;
    }
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
