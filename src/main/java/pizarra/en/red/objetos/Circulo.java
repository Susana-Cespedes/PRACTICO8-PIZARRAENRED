package pizarra.en.red.objetos;

public class Circulo extends Figura {

    private int diametro;

    public Circulo(int x, int y, int d) {
        super(x, y);
        this.diametro = d;
    }

    public int getDiametro() { return diametro; }

    public String serializar() {
        return "CIRCULO " + x + " " + y + " " + diametro;
    }
}

