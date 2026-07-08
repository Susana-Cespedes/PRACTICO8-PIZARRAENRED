package pizarra.en.red.objetos;

public class Cuadrado extends Figura {

    private int ancho, alto; // mejor privado

    public Cuadrado(int x, int y, int ancho, int alto) {
        super(x, y);
        this.ancho = ancho;
        this.alto = alto;
    }

    public int getAncho() { return ancho; }
    public int getAlto() { return alto; }

    public String serializar() {
        return "CUADRADO " + x + " " + y + " " + ancho + " " + alto;
    }
}