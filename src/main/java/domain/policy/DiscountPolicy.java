package domain.policy;

import DTO.DiscountDTO;

import java.security.Policy;
import java.util.ArrayList;
import java.util.List;

public class DiscountPolicy implements Discount {
    private List<Discount> discounts;

    public DiscountPolicy() {
        this.discounts = new ArrayList<>();
    }

    public DiscountPolicy(DiscountPolicy discountPolicy) {
        this.discounts=new ArrayList<>();
        for(Discount discount : discountPolicy.getDiscounts()) {
            if(discount instanceof CodeCoupun){
                this.discounts.add(new CodeCoupun((CodeCoupun) discount));
            }if(discount instanceof LimitedDiscount){
                this.discounts.add(new LimitedDiscount((LimitedDiscount)discount));
            } if(discount instanceof VisualDiscount){
                this.discounts.add(new VisualDiscount((VisualDiscount)discount));
            }
        }
    }

    public static Discount dtoToDiscount(DiscountDTO discount) {
        if(discount==null){
            return null;
        }
        if(discount.getType()== DiscountDTO.Type.LIMITED){
            return new LimitedDiscount(discount.getPercentage(), discount.getMinQuantity());
        }
        if(discount.getType()== DiscountDTO.Type.CODE_COUPON){
            return new CodeCoupun(discount.getCode(), discount.getPercentage(),discount.getEndDate());
        }
        if(discount.getType()== DiscountDTO.Type.VISUAL){
            return new VisualDiscount(discount.getPercentage(), discount.getEndDate());
        }
        if (discount.getType()==DiscountDTO.Type.Policy){
            Discount discountPolicy = new DiscountPolicy();
            for(Discount d : discount.getDiscounts())
                discountPolicy.addDiscount(d);
        }
        return null;
    }

    public void addDiscount(Discount discount) {
        if (!discount.isValid())
            throw new IllegalArgumentException("Invalid discount data");
        if(discountExists(discount))
            throw new RuntimeException("Discount already exists");
        discounts.add(discount);
    }

    public void removeDiscount(Discount discount) {
        for(Discount dis : discounts){
            if (discount.equals(dis)) {
                if(discounts.size()==1)
                    throw new RuntimeException("can't remove discount, there must be at least one discount");
                discounts.remove(dis);
                return;
            }
        }
        throw new RuntimeException("Discount not found");
    }

    public boolean equals(Object discount) {
        if(discount instanceof DiscountPolicy){
            DiscountPolicy discountPolicy = (DiscountPolicy) discount;
            for (Discount dis : discountPolicy.getDiscounts()) {
                boolean equals = false;
                for (Discount disother : this.getDiscounts()) {
                    if(dis.equals(disother))
                        equals = true;
                }
                if(!equals)
                    return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public double apply(double originalPrice, int quantity, String couponCode) {
        double price = originalPrice;
        for (Discount discount : discounts) {
            price = discount.apply(price, quantity, couponCode);
        }
        return price;
    }

    @Override
    public boolean isValid() {
        for (Discount discount : discounts) {
            if (!discount.isValid()) return false;
        }
        return true;
    }

    @Override
    public String describe() {
        if (discounts.isEmpty()) return "No discounts";
        StringBuilder sb = new StringBuilder("Discount policy: ");
        for (int i = 0; i < discounts.size(); i++) {
            sb.append(discounts.get(i).describe());
            if (i < discounts.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public boolean discountExists(Discount newdiscount) {
        for (Discount discount : discounts) {
            if (discount.discountExists(newdiscount)) return true;
        }
        return false;
    }
    public List<Discount> getDiscounts() {
        return new ArrayList<>(discounts);
    }
}
