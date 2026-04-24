package domain.dto;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

public class UserDTO {
    private String email;
    private int userId;
    private String firstName;
    private String lastName;
    private String password;
    private int day;
    private int month;
    private int year;
    private String address;
    private String phone;

    public UserDTO(String  email, String firstName, String lastName, String password,int day, int month, int year, String address, String phone) {
        this.email = email;
        //this.userId = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.day = day;
        this.month = month;
        this.year = year;
        this.address = address;
        this.phone = phone;
    }

    public int getUserId() { return userId; }
    public String getEmail() { return email; }
    public int getAge() {
        LocalDate birthDate = LocalDate.of(this.year, this.month, this.day);
        LocalDate currentDate = LocalDate.now();
        return Period.between(birthDate, currentDate).getYears();
    }
    public String getFirstName() { return firstName;}
    public String getLastName() { return lastName;}
    public int getDay() { return day; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public String getPassword() { return password; }
    public String getAddress() { return address;}
    public String getPhone() { return phone; }
}
