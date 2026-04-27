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
    public ContactInfo(ContactInfo contactInfo) {
        this.email = contactInfo.getEmail();
        this.phone = contactInfo.getPhone();
        this.bankAccount = contactInfo.getBankAccount();
    }

    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getBankAccount() { return bankAccount; }
}
