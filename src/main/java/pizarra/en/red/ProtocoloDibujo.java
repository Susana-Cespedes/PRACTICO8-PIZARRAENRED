package pizarra.en.red;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pizarra.en.red.objetos.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class ProtocoloDibujo implements Runnable {

    private static final Logger logger = LogManager.getRootLogger();

    // ── Observer ──────────────────────────────────────────────────────────────
    public static final String PROP_FIGURA_REMOTA   = "figuraRemota";
    public static final String PROP_LISTA_RECIBIDA  = "listaRecibida";
    public static final String PROP_CONECTADO       = "conectado";
    public static final String PROP_ERROR           = "error";
    public static final String PROP_MENSAJE_REMOTO  = "mensajeRemoto"; // ← Opcion B: chat

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    // ── Red ───────────────────────────────────────────────────────────────────
    private final Socket           socket;
    private final BufferedReader   entrada;
    private final PrintWriter      salida;      // sincronizado en enviar()

    // listaRemota: figuras que llegaron por red desde otros clientes.
    // listaLocal:  figuras propias del operador del servidor -- se necesita
    //              acá SOLO para poder incluirlas en la respuesta a "LISTA".
    private final ListaFiguras     listaRemota;
    private final ListaFiguras     listaLocal;
    private final ServidorDibujo   servidor;    // puede ser null si es cliente

    // ── Constructor servidor ──────────────────────────────────────────────────
    public ProtocoloDibujo(Socket socket, ListaFiguras listaRemota, ListaFiguras listaLocal,
                            ServidorDibujo srv) throws Exception {
        this.socket      = socket;
        this.listaRemota = listaRemota;
        this.listaLocal  = listaLocal;
        this.servidor    = srv;
        this.entrada     = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        this.salida      = new PrintWriter(socket.getOutputStream(), true);
    }

    // ── Constructor cliente (sin servidor) ────────────────────────────────────
    public ProtocoloDibujo(Socket socket) throws Exception {
        this(socket, new ListaFiguras(), new ListaFiguras(), null);
    }

    // ── Protocolo (hilo dedicado) ─────────────────────────────────────────────
    @Override
    public void run() {
        try {
            boolean fin = false;
            while (!fin) {
                String cmd = entrada.readLine();
                if (cmd == null) break;
                logger.info("CMD RECIBIDO: " + cmd);

                switch (cmd) {
                    case "HOLA"    -> manejarHola();
                    case "LISTA"   -> manejarLista();
                    case "FIGURA"  -> manejarFigura();
                    case "MENSAJE" -> manejarMensaje();
                    case "CHAU"    -> { manejarChau(); fin = true; }
                    default        -> logger.warn("CMD DESCONOCIDO: " + cmd);
                }
            }
        } catch (Exception e) {
            logger.warn("ERROR EN PROTOCOLO: " + e.getMessage());
            pcs.firePropertyChange(PROP_ERROR, null, e.getMessage());
        }
    }

    // ── Comandos ──────────────────────────────────────────────────────────────

    private void manejarHola() {
        enviar("OK");
        logger.info("HOLA recibido → OK");
        pcs.firePropertyChange(PROP_CONECTADO, false, true);
    }

    private void manejarLista() throws IOException {
        synchronized (listaRemota) {
            synchronized (listaLocal) {
                int total = listaLocal.size() + listaRemota.size();
                enviar(String.valueOf(total));

                for (Figura f : listaLocal.getFiguras()) {
                    enviar(f.serializar());
                    logger.info("ENVIANDO LISTA (propia del servidor): " + f.serializar());
                }
                for (Figura f : listaRemota.getFiguras()) {
                    enviar(f.serializar());
                    logger.info("ENVIANDO LISTA (de otro cliente): " + f.serializar());
                }
            }
        }
    }

    private void manejarFigura() throws IOException {
        String serializada = entrada.readLine();
        if (serializada == null) return;
        logger.info("FIGURA RECIBIDA: " + (serializada.length() > 80
                ? serializada.substring(0, 80) + "..." : serializada));

        Figura nueva = deserializar(serializada);
        if (nueva == null) return;

        synchronized (listaRemota) {
            listaRemota.agregar(nueva);
        }

        pcs.firePropertyChange(PROP_FIGURA_REMOTA, null, nueva);

        if (servidor != null) {
            servidor.enviarATodos("FIGURA", serializada, socket);
            logger.info("REENVIADO");
        }
    }

    // ── Opcion B: chat de texto sobre el mismo socket ─────────────────────────
    private void manejarMensaje() throws IOException {
        String texto = entrada.readLine();
        if (texto == null) return;
        logger.info("MENSAJE RECIBIDO: " + texto);

        pcs.firePropertyChange(PROP_MENSAJE_REMOTO, null, texto);

        if (servidor != null) {
            servidor.enviarATodos("MENSAJE", texto, socket);
            logger.info("MENSAJE REENVIADO: " + texto);
        }
    }

    private void manejarChau() {
        enviar("OK");
        logger.info("CHAU → cerrando conexión");
    }

    // ── Envío de lista inicial (cliente llama esto tras HOLA) ─────────────────
    public void pedirLista() throws IOException {
        enviar("LISTA");
        int n = Integer.parseInt(entrada.readLine());
        ListaFiguras recibidas = new ListaFiguras();
        for (int i = 0; i < n; i++) {
            String linea = entrada.readLine();
            Figura f = deserializar(linea);
            if (f != null) recibidas.agregar(f);
        }
        logger.info("LISTA RECIBIDA: " + n + " figuras");
        pcs.firePropertyChange(PROP_LISTA_RECIBIDA, null, recibidas);
    }

    // ── Envío de una figura (cliente → servidor) ──────────────────────────────
    public synchronized void enviarFigura(Figura f) {
        String serializada = f.serializar();
        logger.info("ENVIANDO FIGURA (" + serializada.length() + " caracteres)");
        enviar("FIGURA");
        enviar(serializada);
    }

    public synchronized void enviarMensaje(String texto) {
        logger.info("ENVIANDO MENSAJE: " + texto);
        enviar("MENSAJE");
        enviar(texto);
    }

    public synchronized void enviarHola() {
        enviar("HOLA");
    }

    public synchronized void enviarChau() {
        enviar("CHAU");
    }

    // ── Escucha continua desde servidor (modo cliente) ────────────────────────
    public void escuchar() {
        try {
            String linea;
            logger.info("ESCUCHANDO SERVIDOR...");
            while ((linea = entrada.readLine()) != null) {
                logger.info("RECIBIDO: " + linea);

                if (linea.equals("FIGURA")) {
                    String serializada = entrada.readLine();
                    if (serializada == null) continue;

                    Figura f = deserializar(serializada);
                    if (f != null) {
                        logger.info("FIGURA REMOTA RECIBIDA");
                        pcs.firePropertyChange(PROP_FIGURA_REMOTA, null, f);
                    }
                } else if (linea.equals("MENSAJE")) {
                    String texto = entrada.readLine();
                    if (texto == null) continue;
                    logger.info("MENSAJE REMOTO: " + texto);
                    pcs.firePropertyChange(PROP_MENSAJE_REMOTO, null, texto);
                }
            }
        } catch (Exception e) {
            logger.warn("ERROR AL ESCUCHAR: " + e.getMessage());
            pcs.firePropertyChange(PROP_ERROR, null, e.getMessage());
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private synchronized void enviar(String msg) {
        salida.println(msg);
    }

    /**
     * Reconstruye una Figura a partir de su forma serializada.
     * "IMAGEN" se maneja aparte porque el último campo (el Base64) puede
     * contener el mismo caracter que separaría columnas si no se limita
     * la cantidad de "splits" -- por eso se usa split(" ", 5).
     */
    public static Figura deserializar(String linea) {
        if (linea == null || linea.isBlank()) return null;

        if (linea.startsWith("IMAGEN ")) {
            String[] p = linea.split(" ", 6);
            try {
                return Imagen.deserializar(p);
            } catch (Exception e) {
                LogManager.getRootLogger().warn("IMAGEN MAL FORMADA");
                return null;
            }
        }

        String[] p = linea.split(" ");
        try {
            return switch (p[0]) {
                case "CUADRADO" -> new Cuadrado(
                        Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                case "CIRCULO"  -> new Circulo(
                        Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]));
                case "LINEA"    -> new Linea(
                        Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                default -> null;
            };
        } catch (NumberFormatException e) {
            LogManager.getRootLogger().warn("FIGURA MAL FORMADA: " + linea);
            return null;
        }
    }

    public String leerRespuesta() throws IOException {
        String resp = entrada.readLine();
        logger.info("RESPUESTA SERVIDOR: " + resp);
        return resp;
    }

    // ── Opcion A: reconexión ──────────────────────────────────────────────────
    public void cerrar() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("ERROR AL CERRAR SOCKET: " + e.getMessage());
        }
    }
}
