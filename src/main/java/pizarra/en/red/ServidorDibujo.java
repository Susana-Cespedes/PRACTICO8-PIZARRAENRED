package pizarra.en.red;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.gui.VentanaDibujo;
import pizarra.en.red.listas.Lista;
import pizarra.en.red.objetos.ListaFiguras;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServidorDibujo {

    private static final Logger logger = LogManager.getRootLogger();

    private final ServerSocket server;
    private final VentanaDibujo ventana;

    // listaLocal:  lo que dibujó el propio operador del servidor.
    // listaRemota: lo que llegó por red desde otros clientes.
    private final ListaFiguras listaLocal;
    private final ListaFiguras listaRemota;

    // Buena práctica: no se usa Thread.stop()/kill(); un boolean (volatile,
    // porque lo lee el hilo del servidor y lo escribe el hilo de Swing) le
    // avisa al bucle de accept() que debe terminar.
    private volatile boolean pararServidor = false;

    private final CopyOnWriteArrayList<ClienteConexion> clientes = new CopyOnWriteArrayList<>();

    private static class ClienteConexion {
        final Socket      socket;
        final PrintWriter writer;

        ClienteConexion(Socket socket) throws Exception {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public ServidorDibujo(int puerto, VentanaDibujo ventana,
                           ListaFiguras listaLocal, ListaFiguras listaRemota)
            throws Exception {
        this.server      = new ServerSocket(puerto);
        this.ventana     = ventana;
        this.listaLocal  = listaLocal;
        this.listaRemota = listaRemota;
        logger.info("SERVIDOR ESCUCHANDO EN PUERTO " + puerto);
    }

    // ── Bucle principal ───────────────────────────────────────────────────────
    public void iniciar() throws Exception {
        while (!pararServidor) {
            Socket socket = null;
            try {
                socket = server.accept();
                logger.info("CLIENTE CONECTADO: " + socket.getRemoteSocketAddress());

                ClienteConexion conexion = new ClienteConexion(socket);
                clientes.add(conexion);

                if (ventana != null) ventana.clienteConectado();

                ProtocoloDibujo protocolo = new ProtocoloDibujo(socket, listaRemota, listaLocal, this);

                if (ventana != null) {
                    protocolo.addPropertyChangeListener(ventana);
                }

                new Thread(protocolo, "cliente-" + socket.getPort()).start();

            } catch (Exception e) {
                // pararServidor() cierra "server" a propósito, y eso hace
                // que accept() lance esta misma excepción -- no es un error
                // real, es la forma en que el hilo se entera de que debe
                // terminar.
                if (pararServidor) {
                    logger.info("Servidor detenido, saliendo del bucle de accept()");
                } else {
                    logger.warn("ERROR AL ACEPTAR CLIENTE: " + e.getMessage());
                    cerrarSocket(socket);
                }
            }
        }
        logger.info("Hilo del servidor terminado");
    }

    /**
     * Para el servidor sin usar Thread.stop()/kill(): marca el boolean y
     * cierra el ServerSocket para desbloquear el accept() que está esperando
     * clientes -- mismo mecanismo que se usa en ServidorWeb (Práctico 6).
     */
    public void pararServidor() {
        pararServidor = true;
        if (server != null && !server.isClosed()) {
            try {
                server.close();
            } catch (IOException e) {
                logger.warn("Error al cerrar el ServerSocket al parar: " + e.getMessage());
            }
        }
    }

    // ── Broadcast a todos menos el emisor ─────────────────────────────────────
    public synchronized void enviarATodos(String linea1, String linea2, Socket emisor) {
        logger.info("REENVIANDO A CLIENTES: " + linea1 + " / " + linea2);

        Lista<ClienteConexion> caidos = new Lista<>();

        for (ClienteConexion c : clientes) {
            if (emisor != null && c.socket == emisor) continue;
            try {
                c.writer.println(linea1);
                c.writer.println(linea2);
            } catch (Exception e) {
                logger.warn("CLIENTE CAÍDO: " + c.socket.getRemoteSocketAddress());
                caidos.insertar(c);
            }
        }

        for (ClienteConexion c : caidos) {
            clientes.remove(c);
            cerrarSocket(c.socket);
        }
    }

    public void removerCliente(Socket socket) {
        clientes.removeIf(c -> c.socket == socket);
        cerrarSocket(socket);
        logger.info("CLIENTE REMOVIDO: " + socket.getRemoteSocketAddress());
    }

    private void cerrarSocket(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (Exception ignored) {}
    }
}
