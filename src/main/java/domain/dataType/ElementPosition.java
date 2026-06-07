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
}
