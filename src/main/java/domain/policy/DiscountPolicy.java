package domain.policy;

import DTO.DiscountDTO;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discount_policies")
public abstract class DiscountPolicy extends DiscountNode {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id")
    protected List<DiscountNode> discounts;

    protected DiscountPolicy() {
        this.discounts = new ArrayList<>();
    }

    protected DiscountPolicy(List<DiscountNode> discounts) {
        this.discounts = new ArrayList<>(discounts);
    }

    @Override
    public abstract double apply(double originalPrice, int quantity, String couponCode);

    protected abstract String policyName();

    public abstract DiscountPolicyType getPolicyType();

    public abstract DiscountPolicy copyPolicy();

    @Override
    public Discount copy() { return copyPolicy(); }

    protected List<DiscountNode> copyDiscounts() {
        List<DiscountNode> copied = new ArrayList<>();
        for (DiscountNode d : discounts) copied.add((DiscountNode) d.copy());
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
            default: {
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
        discounts.add((DiscountNode) discount);
    }

    public void removeDiscount(Discount discount) {
        for (int i = 0; i < discounts.size(); i++) {
            if (discount.equals(discounts.get(i))) {
                discounts.remove(i);
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
