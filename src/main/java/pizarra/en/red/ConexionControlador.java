package pizarra.en.red;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.gui.VentanaDibujo;
import pizarra.en.red.objetos.Figura;
import pizarra.en.red.objetos.ListaFiguras;

import java.net.Socket;

/**
 * Controlador de conexión: sabe cómo levantar el servidor, conectarse
 * como cliente (con reconexión automática si se cae la red), desconectar
 * (ya sea el cliente o el servidor, según el rol activo), y hacia dónde
 * mandar cada figura o mensaje.
 */

public class ConexionControlador {

    private static final Logger logger = LogManager.getRootLogger();
    private static final long REINTENTO_MS = 3000;

    private final ListaFiguras listaLocal;
    private final VentanaDibujo ventana;

    private ProtocoloDibujo protocolo; // no nulo si actúo como cliente
    private ServidorDibujo  servidor;   // no nulo si actúo como servidor

    private volatile boolean desconexionIntencional = false;
    private String hostConectado;
    private int    puertoConectado;

    public ConexionControlador(ListaFiguras listaLocal, VentanaDibujo ventana) {
        this.listaLocal = listaLocal;
        this.ventana = ventana;
    }

    /** Usado por VentanaDibujo para decidir si un PROP_ERROR es un error real o solo el cierre pedido por el usuario. */
    public boolean esDesconexionIntencional() {
        return desconexionIntencional;
    }

    // ── Envío (llamado desde VentanaDibujo.onFiguraLocal / onMensajeLocal) ────

    public void enviarFigura(Figura f) {
        if (protocolo != null) {
            protocolo.enviarFigura(f);
        } else if (servidor != null) {
            servidor.enviarATodos("FIGURA", f.serializar(), null);
        }
    }

    public void enviarMensaje(String texto) {
        if (protocolo != null) {
            protocolo.enviarMensaje(texto);
        } else if (servidor != null) {
            servidor.enviarATodos("MENSAJE", texto, null);
        }
    }

    // ── Servidor ───────────────────────────────────────────────────────────────

    public void iniciarServidor(ListaFiguras listaRemota) {
        ventana.mostrarDialogoEspera("Esperando conexión...");
        new Thread(() -> {
            try {
                servidor = new ServidorDibujo(5000, ventana, listaLocal, listaRemota);
                logger.info("SERVIDOR INICIADO");
                ventana.actualizarEstadoConexion("Servidor escuchando en 5000");
                servidor.iniciar();
            } catch (Exception e) {
                logger.warn("ERROR AL INICIAR SERVIDOR: " + e.getMessage());
            }
        }, "servidor-dibujo").start();
    }

    // ── Cliente ────────────────────────────────────────────────────────────────

    public void conectar(String host, int puerto) {
        this.hostConectado = host;
        this.puertoConectado = puerto;
        this.desconexionIntencional = false;
        ventana.mostrarDialogoEspera("Conectando...");
        new Thread(this::bucleConexion, "conexion-cliente").start();
    }

    /**
     * Mientras el usuario no haya pedido desconectarse explícitamente, si
     * la conexión se cae (o nunca se pudo establecer), se reintenta cada
     * REINTENTO_MS milisegundos en vez de simplemente rendirse.
     */
    private void bucleConexion() {
        int intento = 0;
        while (!desconexionIntencional) {
            intento++;
            try {
                logger.info("INTENTO DE CONEXION #" + intento);
                Socket socket = new Socket(hostConectado, puertoConectado);
                protocolo = new ProtocoloDibujo(socket);
                protocolo.addPropertyChangeListener(ventana);

                protocolo.enviarHola();
                protocolo.leerRespuesta();
                protocolo.pedirLista();

                synchronized (listaLocal) {
                    for (Figura f : listaLocal.getFiguras()) {
                        protocolo.enviarFigura(f);
                    }
                }

                ventana.actualizarEstadoConexion("Conectado a " + hostConectado + ":" + puertoConectado);
                ventana.cerrarDialogoEspera();
                intento = 0;

                protocolo.escuchar(); // bloquea mientras la conexión esté viva

                if (!desconexionIntencional) {
                    ventana.actualizarEstadoConexion("Conexión perdida, reintentando...");
                    logger.warn("CONEXION PERDIDA, reintentando en " + REINTENTO_MS + "ms");
                    Thread.sleep(REINTENTO_MS);
                }
            } catch (Exception e) {
                if (desconexionIntencional) break;
                logger.warn("ERROR AL CONECTAR (intento " + intento + "): " + e.getMessage());
                ventana.actualizarEstadoConexion("No se pudo conectar, reintentando...");
                try {
                    Thread.sleep(REINTENTO_MS);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
        ventana.cerrarDialogoEspera();
        ventana.actualizarEstadoConexion("Desconectado");
    }

    /**
     * Desconecta lo que esté activo: si soy cliente, cierro mi conexión al
     * servidor; si soy servidor, paro el servidor (con el mismo mecanismo
     * boolean + cerrar socket que ServidorDibujo.pararServidor()). Antes
     * esto solo desconectaba al cliente y dejaba el servidor corriendo de
     * fondo sin que se notara.
     */
    public void desconectar() {
        desconexionIntencional = true;

        if (protocolo != null) {
            try {
                protocolo.enviarChau();
            } catch (Exception ignored) {
                // puede fallar si la conexión ya estaba caída, no importa
            }
            protocolo.cerrar();
            protocolo = null;
        }

        if (servidor != null) {
            servidor.pararServidor();
            servidor = null;
        }

        ventana.actualizarEstadoConexion("Desconectado");
    }
}
