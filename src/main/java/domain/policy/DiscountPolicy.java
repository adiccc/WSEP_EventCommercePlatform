package domain.policy;

import DTO.DiscountDTO;
import java.util.ArrayList;
import java.util.List;

public abstract class DiscountPolicy implements Discount {
    protected List<Discount> discounts;

    protected DiscountPolicy() {
        this.discounts = new ArrayList<>();
    }

    protected DiscountPolicy(List<Discount> discounts) {
        this.discounts = new ArrayList<>(discounts);
    }

    @Override
    public abstract double apply(double originalPrice, int quantity, String couponCode);

    protected abstract String policyName();

    public abstract DiscountPolicyType getPolicyType();

    public abstract DiscountPolicy copyPolicy();

    @Override
    public Discount copy() { return copyPolicy(); }

    protected List<Discount> copyDiscounts() {
        List<Discount> copied = new ArrayList<>();
        for (Discount d : discounts) copied.add(d.copy());
        return copied;
    }

    public static Discount dtoToDiscount(DiscountDTO dto) {
        if (dto == null) return null;
        switch (dto.getType()) {
            case MIN_QUANTITY: return new MinQuantityDiscount(dto.getPercentage(), dto.getQuantity());
            case MAX_QUANTITY: return new MaxQuantityDiscount(dto.getPercentage(), dto.getQuantity());
            case DATE_RANGE:   return new DateRangeDiscount(dto.getPercentage(),
                                   dto.getStartDate().atStartOfDay(), dto.getEndDate().atStartOfDay());
            case CODE_COUPON:  return new CodeCoupun(dto.getCode(), dto.getPercentage(), dto.getEndDate());
            case VISUAL:       return new VisualDiscount(dto.getPercentage(), dto.getEndDate());
            case MAX_POLICY: {
                MaxDiscountPolicy policy = new MaxDiscountPolicy();
                if (dto.getDiscounts() != null)
                    for (DiscountDTO d : dto.getDiscounts()) policy.addDiscount(dtoToDiscount(d));
                return policy;
            }
            default: {  // SUM_POLICY
                SumDiscountPolicy policy = new SumDiscountPolicy();
                if (dto.getDiscounts() != null)
                    for (DiscountDTO d : dto.getDiscounts()) policy.addDiscount(dtoToDiscount(d));
                return policy;
            }
        }
    }

    @Override
    public void addDiscount(Discount discount) {
        if (!discount.isValid())
            throw new IllegalArgumentException("Invalid discount data");
        if (discountExists(discount))
            throw new RuntimeException("Discount already exists");
        discounts.add(discount);
    }

    public void removeDiscount(Discount discount) {
        for (Discount dis : discounts) {
            if (discount.equals(dis)) {
                if (discounts.size() == 1)
                    throw new RuntimeException("can't remove discount, there must be at least one discount");
                discounts.remove(dis);
                return;
            }
        }
        throw new RuntimeException("Discount not found");
    }

    @Override
    public boolean isValid() {
        for (Discount discount : discounts)
            if (!discount.isValid()) return false;
        return true;
    }

    @Override
    public String describe() {
        if (discounts.isEmpty()) return "No discounts";
        StringBuilder sb = new StringBuilder(policyName() + ": ");
        for (int i = 0; i < discounts.size(); i++) {
            sb.append(discounts.get(i).describe());
            if (i < discounts.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public boolean discountExists(Discount newdiscount) {
        for (Discount discount : discounts)
            if (discount.discountExists(newdiscount)) return true;
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiscountPolicy) {
            DiscountPolicy other = (DiscountPolicy) obj;
            for (Discount dis : other.discounts) {
                boolean found = false;
                for (Discount mine : this.discounts)
                    if (dis.equals(mine)) { found = true; break; }
                if (!found) return false;
            }
            return true;
        }
        return false;
    }

    public List<Discount> getDiscounts() { return new ArrayList<>(discounts); }
}
