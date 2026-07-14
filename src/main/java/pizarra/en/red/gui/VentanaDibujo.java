package pizarra.en.red.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.ConexionControlador;
import pizarra.en.red.ProtocoloDibujo;
import pizarra.en.red.historial.HistorialSesion;
import pizarra.en.red.objetos.*;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class VentanaDibujo extends JFrame implements PropertyChangeListener {

    private static final Logger logger = LogManager.getRootLogger();

    // ── Listas ────────────────────────────────────────────────────────────────
    private final ListaFiguras listaLocal   = new ListaFiguras();
    private final ListaFiguras listaRemota  = new ListaFiguras();

    // ── Colaboradores ───────────────────────────────────────────────────────────
    private final ConexionControlador controlador  = new ConexionControlador(listaLocal, this);
    private final DialogoEspera       dialogoEspera = new DialogoEspera(this);
    private final HistorialSesion     historial     = crearHistorial();

    private static HistorialSesion crearHistorial() {
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        long pid = ProcessHandle.current().pid();
        String nombreArchivo = "historial_pizarra_" + marca + "_" + pid + ".log";
        return new HistorialSesion(nombreArchivo);
    }

    // ── GUI ───────────────────────────────────────────────────────────────────
    private PanelDibujo panel;
    private PanelChat   panelChat;
    private JLabel       lblEstadoConexion;

    // ── Constructor ───────────────────────────────────────────────────────────
    public VentanaDibujo() {
        logger.info("HISTORIAL DE ESTA INSTANCIA: " + historial.getRutaAbsoluta());

        setTitle("Pizarra en Red");
        setSize(950, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        panel = new PanelDibujo(listaLocal, listaRemota, this);
        panelChat = new PanelChat(this::onMensajeLocal);

        setJMenuBar(construirMenu());
        add(construirToolbar(), BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(panelChat, BorderLayout.EAST);

        lblEstadoConexion = new JLabel("Estado: sin conexión");
        add(lblEstadoConexion, BorderLayout.SOUTH);

        setVisible(true);
    }

    // ── Observer: recibe eventos de ProtocoloDibujo (vía el controlador) ──────
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        switch (evt.getPropertyName()) {

            case ProtocoloDibujo.PROP_FIGURA_REMOTA -> {
                Figura f = (Figura) evt.getNewValue();
                synchronized (listaLocal) {
                    if (listaLocal.contiene(f)) return;
                }
                synchronized (listaRemota) {
                    if (!listaRemota.contiene(f)) {
                        listaRemota.agregar(f);
                        logger.info("FIGURA REMOTA AGREGADA");
                    }
                }
                historial.registrar(HistorialSesion.TIPO_FIGURA, HistorialSesion.ORIGEN_REMOTO, f.serializar());
                SwingUtilities.invokeLater(panel::repaint);
            }

            case ProtocoloDibujo.PROP_LISTA_RECIBIDA -> {
                ListaFiguras recibidas = (ListaFiguras) evt.getNewValue();
                synchronized (listaRemota) {
                    listaRemota.limpiar();
                    for (Figura f : recibidas.getFiguras()) {
                        synchronized (listaLocal) {
                            if (!listaLocal.contiene(f)) listaRemota.agregar(f);
                        }
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    panel.repaint();
                    JOptionPane.showMessageDialog(this, "Conectado correctamente");
                });
            }

            case ProtocoloDibujo.PROP_CONECTADO -> dialogoEspera.cerrar();

            case ProtocoloDibujo.PROP_MENSAJE_REMOTO -> {
                String texto = (String) evt.getNewValue();
                historial.registrar(HistorialSesion.TIPO_MENSAJE, HistorialSesion.ORIGEN_REMOTO, texto);
                SwingUtilities.invokeLater(() -> panelChat.agregarRemoto(texto));
            }

            case ProtocoloDibujo.PROP_ERROR -> {
                // Si la desconexión fue pedida por el usuario, este error es
                // esperado (el controlador cerró el socket a propósito).
                if (controlador.esDesconexionIntencional()) return;
                String msg = (String) evt.getNewValue();
                actualizarEstadoConexion("Conexión perdida (" + msg + ")");
            }
        }
    }

    // ── Llamado por PanelDibujo cuando el usuario dibuja algo (o carga una imagen) ──
    public void onFiguraLocal(Figura f) {
        synchronized (listaLocal) { listaLocal.agregar(f); }
        logger.info("FIGURA LOCAL CREADA");
        historial.registrar(HistorialSesion.TIPO_FIGURA, HistorialSesion.ORIGEN_LOCAL, f.serializar());
        controlador.enviarFigura(f);
    }

    // ── Llamado por PanelChat cuando el usuario envía un mensaje ──────────────
    private void onMensajeLocal(String texto) {
        controlador.enviarMensaje(texto);
        historial.registrar(HistorialSesion.TIPO_MENSAJE, HistorialSesion.ORIGEN_LOCAL, texto);
        panelChat.agregarPropio(texto);
    }

    // ── Menú ──────────────────────────────────────────────────────────────────
    private JMenuBar construirMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");

        JMenuItem servidor    = new JMenuItem("Comenzar Servidor");
        JMenuItem conectar    = new JMenuItem("Conectar");
        JMenuItem desconectar = new JMenuItem("Desconectar");
        JMenuItem reproducir  = new JMenuItem("Reproducir Historial");
        JMenuItem limpiar     = new JMenuItem("Limpiar");
        JMenuItem salir       = new JMenuItem("Salir");

        servidor.addActionListener(e -> controlador.iniciarServidor(listaRemota));
        conectar.addActionListener(e -> conectarDialogo());
        desconectar.addActionListener(e -> controlador.desconectar());
        reproducir.addActionListener(e -> reproducirHistorial());
        limpiar.addActionListener(e -> limpiar());
        salir.addActionListener(e -> System.exit(0));

        archivo.add(servidor);
        archivo.add(conectar);
        archivo.add(desconectar);
        archivo.addSeparator();
        archivo.add(reproducir);
        archivo.add(limpiar);
        archivo.addSeparator();
        archivo.add(salir);
        menuBar.add(archivo);
        return menuBar;
    }

    private JToolBar construirToolbar() {
        JToolBar tb = new JToolBar();
        JButton cuadrado = new JButton("Cuadrado");
        JButton circulo  = new JButton("Círculo");
        JButton linea    = new JButton("Línea");
        JButton imagen   = new JButton("Imagen");

        cuadrado.addActionListener(e -> panel.setFigura("CUADRADO"));
        circulo .addActionListener(e -> panel.setFigura("CIRCULO"));
        linea   .addActionListener(e -> panel.setFigura("LINEA"));
        imagen  .addActionListener(e -> panel.setFigura("IMAGEN"));

        tb.add(cuadrado);
        tb.add(circulo);
        tb.add(linea);
        tb.add(imagen);
        return tb;
    }

    // ── Diálogo para pedir host/puerto -- GUI pura, se queda acá ──────────────
    private void conectarDialogo() {
        JTextField hostField   = new JTextField("localhost");
        JTextField puertoField = new JTextField("5000");
        JPanel panelInput = new JPanel(new GridLayout(4, 1));
        panelInput.add(new JLabel("Host:"));
        panelInput.add(hostField);
        panelInput.add(new JLabel("Puerto:"));
        panelInput.add(puertoField);

        int opcion = JOptionPane.showConfirmDialog(
                this, panelInput, "Conectar a servidor",
                JOptionPane.OK_CANCEL_OPTION);

        if (opcion != JOptionPane.OK_OPTION) return;

        try {
            int puerto = Integer.parseInt(puertoField.getText().trim());
            controlador.conectar(hostField.getText().trim(), puerto);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Puerto inválido");
            logger.warn("PUERTO INVALIDO");
        }
    }

    // ── Reproducción del historial guardado ───────────────────────────────────
    private void reproducirHistorial() {
        if (!historial.existe()) {
            JOptionPane.showMessageDialog(this, "Todavía no hay historial guardado.");
            return;
        }

        synchronized (listaLocal)  { listaLocal.limpiar(); }
        synchronized (listaRemota) { listaRemota.limpiar(); }
        panelChat.limpiar();
        panel.repaint();
        panelChat.agregarAviso("--- Reproduciendo historial ---");

        historial.reproducir(ev -> SwingUtilities.invokeLater(() -> {
            boolean esLocal = HistorialSesion.ORIGEN_LOCAL.equals(ev.origen);

            if (HistorialSesion.TIPO_FIGURA.equals(ev.tipo)) {
                Figura f = ProtocoloDibujo.deserializar(ev.datos);
                if (f != null) {
                    if (esLocal) {
                        synchronized (listaLocal) { listaLocal.agregar(f); }
                    } else {
                        synchronized (listaRemota) { listaRemota.agregar(f); }
                    }
                    panel.repaint();
                }
            } else if (HistorialSesion.TIPO_MENSAJE.equals(ev.tipo)) {
                panelChat.agregarHistorial(esLocal ? "Yo" : "Otro", ev.datos);
            }
        }), 400);
    }

    // ── Limpiar: reinicia TODO lo que se ve en esta ventana ───────────────────
    private void limpiar() {
        synchronized (listaLocal)  { listaLocal.limpiar(); }
        synchronized (listaRemota) { listaRemota.limpiar(); }
        panelChat.limpiar();
        panel.repaint();
    }

    // ── Métodos públicos que llama ConexionControlador (otro paquete) ────────
    public void mostrarDialogoEspera(String mensaje) {
        dialogoEspera.mostrar(mensaje);
    }

    public void cerrarDialogoEspera() {
        dialogoEspera.cerrar();
    }

    public void actualizarEstadoConexion(String texto) {
        SwingUtilities.invokeLater(() -> lblEstadoConexion.setText("Estado: " + texto));
    }

    // Llamado por ServidorDibujo cuando un cliente se conecta
    public void clienteConectado() {
        dialogoEspera.cerrar();
    }

    public static void main(String[] args) {
        new VentanaDibujo();
    }
}
