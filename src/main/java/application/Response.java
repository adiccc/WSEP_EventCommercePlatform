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

    public boolean isError() {
        return message != null;
    }

    public static <T> Response<T> ok(T value) {
        return new Response<>(value, null);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(null, message);
    }
}
