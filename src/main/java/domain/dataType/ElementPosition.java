package domain.dataType;

import jakarta.persistence.Embeddable;

@Embeddable
public class ElementPosition {
    private double x;
    private double y;
    public ElementPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    protected ElementPosition() {
        // JPA
    }
    public ElementPosition(ElementPosition elementPosition) {
        this.x = elementPosition.x;
        this.y = elementPosition.y;
    }
    public double getX() {
        return x;
    }
    public void setX(double x) {
        this.x = x;
    }
    public double getY() {
        return y;
    }
    public void setY(double y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ElementPosition other)) {
            return false;
        }

        return Double.compare(this.x, other.x) == 0
                && Double.compare(this.y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);
    }
}
