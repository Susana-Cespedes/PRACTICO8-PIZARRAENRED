package pizarra.en.red.objetos;

import pizarra.en.red.listas.Lista;

public class ListaFiguras {

    private Lista<Figura> figuras;

    public ListaFiguras() {
        figuras = new Lista<>();
    }

    public void agregar(Figura f) {
        figuras.insertar(f);
    }

    public int size() {
        return figuras.tamano();
    }

    public Lista<Figura> getFiguras() {
        return figuras;
    }

    public void limpiar() {
        figuras = new Lista<>();
    }
    public boolean contiene(Figura f) {

        for (Figura fig : figuras) {
            if (fig.serializar().equals(f.serializar())) {
                return true;
            }
        }
        return false;
    }

}
