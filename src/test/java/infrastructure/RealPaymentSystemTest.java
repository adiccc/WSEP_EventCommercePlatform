package infrastructure;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValidationTest {

    private boolean simulateCvvValidation(String cvv) {
        if (cvv == null) return false;
        return cvv.matches("^\\d{3,4}$");
    }

    private boolean simulateExpiryValidation(String expiryValue) {
        if (expiryValue == null || !expiryValue.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            return false;
        }
        try {
            String[] parts = expiryValue.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = 2000 + Integer.parseInt(parts[1]);
            return !YearMonth.of(year, month).isBefore(YearMonth.now());
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void testCvvValidation() {
        assertTrue(simulateCvvValidation("123"), "3 digits should be valid");
        assertTrue(simulateCvvValidation("1234"), "4 digits should be valid");

        assertFalse(simulateCvvValidation("12"), "2 digits should be invalid");
        assertFalse(simulateCvvValidation("12345"), "5 digits should be invalid");
        assertFalse(simulateCvvValidation("12a"), "Letters should be invalid");
        assertFalse(simulateCvvValidation(null), "Null should be invalid");
    }

    @Test
    void testExpirationDateValidation() {
        int currentYearShort = YearMonth.now().getYear() % 100;
        String validFuture = String.format("12/%02d", currentYearShort + 2);
        String validCurrent = String.format("%02d/%02d", YearMonth.now().getMonthValue(), currentYearShort);

        assertTrue(simulateExpiryValidation(validFuture), "Future date should be valid");
        assertTrue(simulateExpiryValidation(validCurrent), "Current month should be valid");

        assertFalse(simulateExpiryValidation("05/20"), "Past year should be invalid");
        assertFalse(simulateExpiryValidation("13/28"), "Month 13 should be invalid");
        assertFalse(simulateExpiryValidation("00/28"), "Month 00 should be invalid");
        assertFalse(simulateExpiryValidation("abc"), "Wrong format should be invalid");
    }
}