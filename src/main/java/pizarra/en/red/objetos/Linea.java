package pizarra.en.red.objetos;

public class Linea extends Figura {

    private int x2, y2;

    public Linea(int x1, int y1, int x2, int y2) {
        super(x1, y1);
        this.x2 = x2;
        this.y2 = y2;
    }

    public int getX2() { return x2; }
    public int getY2() { return y2; }

    public String serializar() {
        return "LINEA " + x + " " + y + " " + x2 + " " + y2;
    }
}
