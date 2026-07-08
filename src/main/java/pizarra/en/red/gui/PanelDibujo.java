package pizarra.en.red.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.objetos.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PanelDibujo extends JPanel {

    private static final Logger logger = LogManager.getRootLogger();

    private final ListaFiguras local;
    private final ListaFiguras remoto;
    private final VentanaDibujo ventana;

    private String figura = "CUADRADO";
    private int x1, y1;

    public PanelDibujo(ListaFiguras local, ListaFiguras remoto, VentanaDibujo ventana) {
        this.local   = local;
        this.remoto  = remoto;
        this.ventana = ventana;

        setBackground(Color.WHITE);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                x1 = e.getX();
                y1 = e.getY();
                requestFocus();

                // La herramienta "Imagen" no se arrastra como las demás --
                // un solo click alcanza para elegir el archivo y colocarlo.
                if ("IMAGEN".equals(figura)) {
                    cargarImagenEnPosicion(x1, y1);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ("IMAGEN".equals(figura)) return; // ya se manejó en mousePressed

                int x2 = e.getX();
                int y2 = e.getY();

                Figura nueva = crearFigura(x1, y1, x2, y2);
                if (nueva == null) return;

                logger.info("FIGURA DIBUJADA: " + nueva.serializar());
                ventana.onFiguraLocal(nueva);
                repaint();
            }
        });
    }

    // ── Creación de figura según herramienta activa ───────────────────────────
    // Se normalizan las coordenadas (min/abs) para que dibujar de derecha a
    // izquierda o de abajo hacia arriba funcione igual que de izquierda a
    // derecha.

    private Figura crearFigura(int x1, int y1, int x2, int y2) {
        return switch (figura) {
            case "CUADRADO" -> crearCuadrado(x1, y1, x2, y2);
            case "CIRCULO"  -> crearCirculo(x1, y1, x2, y2);
            case "LINEA"    -> new Linea(x1, y1, x2, y2);
            default -> {
                logger.warn("HERRAMIENTA DESCONOCIDA: " + figura);
                yield null;
            }
        };
    }

    private Cuadrado crearCuadrado(int x1, int y1, int x2, int y2) {
        int x     = Math.min(x1, x2);
        int y     = Math.min(y1, y2);
        int ancho = Math.abs(x2 - x1);
        int alto  = Math.abs(y2 - y1);
        return new Cuadrado(x, y, ancho, alto);
    }

    private Circulo crearCirculo(int x1, int y1, int x2, int y2) {
        int x        = Math.min(x1, x2);
        int y        = Math.min(y1, y2);
        int diametro = Math.abs(x2 - x1);
        return new Circulo(x, y, diametro);
    }

    // ── Cargar y compartir una imagen JPG/PNG ─────────────────────────────────
    // La lectura del archivo y la conversión a píxeles queda delegada en
    // Imagen.desdeArchivo(), que usa VerySimpleImageWriter -- el panel no
    // necesita saber nada de esos detalles.
    private void cargarImagenEnPosicion(int x, int y) {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Imagenes (jpg, png)", "jpg", "jpeg", "png"));
        int opcion = selector.showOpenDialog(this);
        if (opcion != JFileChooser.APPROVE_OPTION) return;

        File archivoElegido = selector.getSelectedFile();
        try {
            Imagen nueva = Imagen.desdeArchivo(x, y, archivoElegido);
            logger.info("IMAGEN CARGADA: " + archivoElegido.getName()
                    + " (" + nueva.getAncho() + "x" + nueva.getAlto() + ")");

            ventana.onFiguraLocal(nueva);
            repaint();
        } catch (IOException e) {
            logger.warn("NO SE PUDO CARGAR LA IMAGEN: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "No se pudo cargar la imagen: " + e.getMessage());
        }
    }

    // ── Herramienta activa ────────────────────────────────────────────────────

    public void setFigura(String modo) {
        this.figura = modo;
    }

    // ── Pintado ───────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        List<Figura> snapLocal;
        List<Figura> snapRemoto;

        synchronized (local) {
            snapLocal = snapshot(local);
        }
        synchronized (remoto) {
            snapRemoto = snapshot(remoto);
        }

        g.setColor(Color.BLUE);
        for (Figura f : snapLocal)  dibujar(g, f);

        g.setColor(Color.RED);
        for (Figura f : snapRemoto) dibujar(g, f);
    }

    private List<Figura> snapshot(ListaFiguras lista) {
        List<Figura> copia = new ArrayList<>();
        for (Figura f : lista.getFiguras()) copia.add(f);
        return copia;
    }

    private void dibujar(Graphics g, Figura f) {
        if      (f instanceof Cuadrado c) g.drawRect(c.getX(), c.getY(), c.getAncho(), c.getAlto());
        else if (f instanceof Circulo  c) g.drawOval(c.getX(), c.getY(), c.getDiametro(), c.getDiametro());
        else if (f instanceof Linea    l) g.drawLine(l.getX(), l.getY(), l.getX2(), l.getY2());
        else if (f instanceof Imagen   img) dibujarImagen(g, img);
    }

    private void dibujarImagen(Graphics g, Imagen img) {
        BufferedImage bi = img.obtenerImagen();
        if (bi == null) return;
        g.drawImage(bi, img.getX(), img.getY(), null);
        // Borde del color del dueño (azul = mío, rojo = del otro).
        g.drawRect(img.getX(), img.getY(), bi.getWidth(), bi.getHeight());
    }
}
