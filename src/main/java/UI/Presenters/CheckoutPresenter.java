package UI.Presenters;

import DTO.CheckoutPriceDTO;
import DTO.PaymentDetailsDTO;
import application.ActiveOrderService;
import application.Response;

public class CheckoutPresenter {

    private final ActiveOrderService activeOrderService;

    public CheckoutPresenter(ActiveOrderService activeOrderService) {
        this.activeOrderService = activeOrderService;
    }

    public Response<CheckoutPriceDTO> prepareCheckout(String token, int activeOrderId) {
        return activeOrderService.prepareCheckout(token, activeOrderId);
    }

    public Response<CheckoutPriceDTO> applyCheckoutCoupon(String token, int activeOrderId, String couponCode) {
        return activeOrderService.applyCheckoutCoupon(token, activeOrderId, couponCode);
    }

    public Response<Integer> checkoutAndPayment(
            String token,
            int activeOrderId,
            PaymentDetailsDTO paymentDetails) {

        return activeOrderService.checkoutAndPayment(token, activeOrderId, paymentDetails);
    }
}