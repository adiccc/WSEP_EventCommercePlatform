package UI.Presenters;

import DTO.CheckoutPriceDTO;
import DTO.PaymentDetailsDTO;
import application.ActiveOrderService;
import application.IAuth;
import application.Response;

public class CheckoutPresenter {

    private final ActiveOrderService activeOrderService;
    private final IAuth auth;


    public CheckoutPresenter(ActiveOrderService activeOrderService, IAuth auth) {
        this.activeOrderService = activeOrderService;
        this.auth = auth;
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

    public Response<String> getRole(String token) {
        return auth.getRole(token);
    }
}