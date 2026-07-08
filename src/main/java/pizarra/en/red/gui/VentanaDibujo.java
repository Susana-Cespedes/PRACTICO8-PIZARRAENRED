package pizarra.en.red.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.ConexionControlador;
import pizarra.en.red.ProtocoloDibujo;
import pizarra.en.red.historial.HistorialSesion;
import pizarra.en.red.objetos.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ventana: se encarga SOLO de la parte gráfica (menú, toolbar, lienzo,
 * chat, diálogos) y de mantener las listas de figuras a dibujar.
 * Todo lo que es "conectarse, reconectar, desconectar, mandar por red"
 * está delegado en ConexionControlador -- esta clase no abre sockets ni
 * maneja hilos de red directamente.
 */
public class VentanaDibujo extends JFrame implements PropertyChangeListener {

    private static final Logger logger = LogManager.getRootLogger();

    // ── Listas ────────────────────────────────────────────────────────────────
    private final ListaFiguras listaLocal   = new ListaFiguras();
    private final ListaFiguras listaRemota  = new ListaFiguras();

    // ── Controlador de conexión (red) ──────────────────────────────────────────
    private final ConexionControlador controlador = new ConexionControlador(listaLocal, this);

    // ── Opcion A: historial / reproducción ────────────────────────────────────
    private final HistorialSesion historial = crearHistorial();

    private static HistorialSesion crearHistorial() {
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        long pid = ProcessHandle.current().pid();
        String nombreArchivo = "historial_pizarra_" + marca + "_" + pid + ".log";
        return new HistorialSesion(nombreArchivo);
    }

    // ── GUI ───────────────────────────────────────────────────────────────────
    private PanelDibujo   panel;
    private JDialog       dialogoEspera;
    private JTextArea     txtChat;
    private JTextField    txtMensaje;
    private JLabel        lblEstadoConexion;

    // ── Constructor ───────────────────────────────────────────────────────────
    public VentanaDibujo() {
        logger.info("HISTORIAL DE ESTA INSTANCIA: " + historial.getRutaAbsoluta());

        setTitle("Pizarra en Red");
        setSize(950, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        panel = new PanelDibujo(listaLocal, listaRemota, this);
        setJMenuBar(construirMenu());
        add(construirToolbar(), BorderLayout.NORTH);
        add(panel, BorderLayout.CENTER);
        add(construirPanelChat(), BorderLayout.EAST);

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

            case ProtocoloDibujo.PROP_CONECTADO -> {
                SwingUtilities.invokeLater(this::cerrarDialogoEspera);
            }

            case ProtocoloDibujo.PROP_MENSAJE_REMOTO -> {
                String texto = (String) evt.getNewValue();
                historial.registrar(HistorialSesion.TIPO_MENSAJE, HistorialSesion.ORIGEN_REMOTO, texto);
                SwingUtilities.invokeLater(() -> txtChat.append("Otro: " + texto + "\n"));
            }

            case ProtocoloDibujo.PROP_ERROR -> {
                // Si la desconexión fue pedida por el usuario, este error es
                // esperado (el controlador cerró el socket a propósito) y no
                // hay que mostrar nada raro ni preocuparse.
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

    // ── Envío de chat, llamado desde el panel de chat ─────────────────────────
    private void onMensajeLocal(String texto) {
        controlador.enviarMensaje(texto);
        historial.registrar(HistorialSesion.TIPO_MENSAJE, HistorialSesion.ORIGEN_LOCAL, texto);
        txtChat.append("Yo: " + texto + "\n");
    }

    // ── Menú ──────────────────────────────────────────────────────────────────
    private JMenuBar construirMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");

        JMenuItem servidor    = new JMenuItem("Comenzar Servidor");
        JMenuItem conectar     = new JMenuItem("Conectar");
        JMenuItem desconectar  = new JMenuItem("Desconectar");
        JMenuItem reproducir   = new JMenuItem("Reproducir Historial");
        JMenuItem limpiar      = new JMenuItem("Limpiar");
        JMenuItem salir        = new JMenuItem("Salir");

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

    // ── Panel de chat ────────────────────────────────────────────────────────
    private JPanel construirPanelChat() {
        JPanel panelChat = new JPanel(new BorderLayout(5, 5));

        txtChat = new JTextArea(8, 20);
        txtChat.setEditable(false);
        txtChat.setLineWrap(true);
        txtChat.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(txtChat);
        scroll.setBorder(BorderFactory.createTitledBorder("Chat"));

        JPanel envio = new JPanel(new BorderLayout(5, 5));
        txtMensaje = new JTextField();
        JButton btnEnviar = new JButton("Enviar");

        ActionListener enviarAccion = e -> {
            String texto = txtMensaje.getText().trim();
            if (!texto.isEmpty()) {
                onMensajeLocal(texto);
                txtMensaje.setText("");
            }
        };
        btnEnviar.addActionListener(enviarAccion);
        txtMensaje.addActionListener(enviarAccion);

        envio.add(txtMensaje, BorderLayout.CENTER);
        envio.add(btnEnviar, BorderLayout.EAST);

        panelChat.add(scroll, BorderLayout.CENTER);
        panelChat.add(envio, BorderLayout.SOUTH);
        panelChat.setPreferredSize(new Dimension(240, 0));
        return panelChat;
    }

    // ── Diálogo para pedir host/puerto -- esto es GUI pura, se queda acá ──────
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
        txtChat.setText("");
        panel.repaint();
        txtChat.append("--- Reproduciendo historial ---\n");

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
                String quien = esLocal ? "Yo" : "Otro";
                txtChat.append("[Historial] " + quien + ": " + ev.datos + "\n");
            }
        }), 400);
    }

    // ── Limpiar: reinicia TODO lo que se ve en esta ventana ───────────────────
    private void limpiar() {
        synchronized (listaLocal)  { listaLocal.limpiar(); }
        synchronized (listaRemota) { listaRemota.limpiar(); }
        txtChat.setText("");
        panel.repaint();
    }

    // ── Diálogo de espera y estado -- llamados también desde ConexionControlador ──
    // Son públicos porque el controlador vive en otro paquete (pizarra.en.red)
    // y necesita poder pedirle a la ventana que actualice la pantalla. Cada uno
    // se encarga de saltar al hilo de Swing (invokeLater) así el controlador no
    // tiene que preocuparse desde qué hilo lo está llamando.

    public void mostrarDialogoEspera(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            dialogoEspera = new JDialog(this, "Conexión", false);
            JLabel label  = new JLabel(mensaje);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            dialogoEspera.add(label);
            dialogoEspera.setSize(250, 100);
            dialogoEspera.setLocationRelativeTo(this);
            dialogoEspera.setVisible(true);
        });
    }

    public void cerrarDialogoEspera() {
        SwingUtilities.invokeLater(() -> {
            if (dialogoEspera != null) {
                dialogoEspera.dispose();
                dialogoEspera = null;
            }
        });
    }

    public void actualizarEstadoConexion(String texto) {
        SwingUtilities.invokeLater(() -> lblEstadoConexion.setText("Estado: " + texto));
    }

    // Llamado por ServidorDibujo cuando un cliente se conecta
    public void clienteConectado() {
        cerrarDialogoEspera();
    }

    public static void main(String[] args) {
        new VentanaDibujo();
    }
}
