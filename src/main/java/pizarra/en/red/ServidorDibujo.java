package pizarra.en.red;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.gui.VentanaDibujo;
import pizarra.en.red.listas.Lista;
import pizarra.en.red.objetos.ListaFiguras;

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
    // Se necesitan ambas para poder responder "LISTA" con el tablero
    // completo a cualquier cliente que se conecte.
    private final ListaFiguras listaLocal;
    private final ListaFiguras listaRemota;

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
        while (true) {
            Socket socket = server.accept();
            logger.info("CLIENTE CONECTADO: " + socket.getRemoteSocketAddress());

            try {
                ClienteConexion conexion = new ClienteConexion(socket);
                clientes.add(conexion);

                if (ventana != null) ventana.clienteConectado();

                ProtocoloDibujo protocolo = new ProtocoloDibujo(socket, listaRemota, listaLocal, this);

                if (ventana != null) {
                    protocolo.addPropertyChangeListener(ventana);
                }

                new Thread(protocolo, "cliente-" + socket.getPort()).start();

            } catch (Exception e) {
                logger.warn("ERROR AL ACEPTAR CLIENTE: " + e.getMessage());
                cerrarSocket(socket);
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
        try { socket.close(); } catch (Exception ignored) {}
    }
}
