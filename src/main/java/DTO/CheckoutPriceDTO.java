package DTO;

public class CheckoutPriceDTO {
    private final double originalPrice;
    private final double finalPrice;
    private final String eventDiscountDescription;
    private final String companyDiscountDescription;

    public CheckoutPriceDTO(double originalPrice,
                            double finalPrice,
                            String eventDiscountDescription,
                            String companyDiscountDescription) {
        this.originalPrice = originalPrice;
        this.finalPrice = finalPrice;
        this.eventDiscountDescription = eventDiscountDescription;
        this.companyDiscountDescription = companyDiscountDescription;
    }

    public double getOriginalPrice() {
        return originalPrice;
    }

    public double getFinalPrice() {
        return finalPrice;
    }

    public String getEventDiscountDescription() {
        return eventDiscountDescription;
    }

    public String getCompanyDiscountDescription() {
        return companyDiscountDescription;
    }
}