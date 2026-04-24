package domain.company;

public class ContactInfo {

    private String email;
    private String phone;
    private String bankAccount;

    public ContactInfo(String email, String phone, String bankAccount) {
        this.email = email;
        this.phone = phone;
        this.bankAccount = bankAccount;
    }

    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getBankAccount() { return bankAccount; }
}
