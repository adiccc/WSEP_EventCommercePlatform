package domain.dto;

public class UserDTO {
    private int userId;
    private int age;

    public UserDTO(int userId, int age) {
        this.userId = userId;
        this.age = age;
    }

    public int getUserId() { return userId; }
    public int getAge() { return age; }
}
