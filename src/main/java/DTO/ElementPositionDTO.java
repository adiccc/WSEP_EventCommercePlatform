package DTO;

public class ElementPositionDTO {
    private int x;
    private int y;
    public ElementPositionDTO(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }
    public double getY() {
        return y;
    }
}
