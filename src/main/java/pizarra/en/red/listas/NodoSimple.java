package pizarra.en.red.listas;

public class NodoSimple {
    private Object contenido;
    private NodoSimple enlace;

    public NodoSimple(Object o) {
        contenido = o;
        enlace = null;
    }

    public Object getContenido() {
        return contenido;
    }

    public void setContenido(Object contenido) {
        this.contenido = contenido;
    }

    public NodoSimple getEnlace() {
        return enlace;
    }

    public void setEnlace(NodoSimple enlace) {
        this.enlace = enlace;
    }

    @Override
    public String toString() {
        return "[" + contenido.toString() + "] -> ";
    }
}
