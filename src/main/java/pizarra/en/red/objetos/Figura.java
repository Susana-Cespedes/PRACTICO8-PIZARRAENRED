package pizarra.en.red.objetos;

public abstract class Figura {

    protected int x, y;

    public Figura(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() { return x; }
    public int getY() { return y; }

    public abstract String serializar();
}