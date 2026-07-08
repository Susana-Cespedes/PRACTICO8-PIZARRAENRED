package pizarra.en.red.historial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Opcion A (Pizarra en Red): guarda cada evento de la sesión (figura
 * dibujada o mensaje de chat) en un archivo de texto plano, y permite
 * "reproducirlo" más tarde, redibujando/mostrando cada evento con una
 * pequeña pausa entre uno y otro.
 *
 * Cada evento guarda también su ORIGEN (LOCAL o REMOTO) para que al
 * reproducir se sepa quién hizo qué -- sin esto, todo se veía igual y
 * no se distinguía "yo" de "el otro".
 *
 * Formato de cada línea: timestamp;TIPO;ORIGEN;datos
 *   2026-07-07T10:15:23.123;FIGURA;LOCAL;CIRCULO 60 100 50
 *   2026-07-07T10:15:25.500;MENSAJE;REMOTO;Hola!
 */
public class HistorialSesion {

    private static final Logger logger = LogManager.getRootLogger();
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String SEP = ";";

    public static final String TIPO_FIGURA  = "FIGURA";
    public static final String TIPO_MENSAJE = "MENSAJE";

    public static final String ORIGEN_LOCAL  = "LOCAL";
    public static final String ORIGEN_REMOTO = "REMOTO";

    private final Path archivo;

    public HistorialSesion(String nombreArchivo) {
        this.archivo = Paths.get(nombreArchivo);
    }

    /** Ruta absoluta del archivo, útil para loguearla al arrancar y saber dónde mirar. */
    public String getRutaAbsoluta() {
        return archivo.toAbsolutePath().normalize().toString();
    }

    /** Agrega un evento al final del archivo. */
    public synchronized void registrar(String tipo, String origen, String datos) {
        String linea = LocalDateTime.now().format(FORMATO) + SEP + tipo + SEP + origen + SEP + datos;
        try (BufferedWriter w = Files.newBufferedWriter(
                archivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(linea);
            w.newLine();
        } catch (IOException e) {
            logger.warn("NO SE PUDO ESCRIBIR HISTORIAL: " + e.getMessage());
        }
    }

    public boolean existe() {
        return Files.exists(archivo);
    }

    public static class Evento {
        public final String tipo;
        public final String origen;
        public final String datos;
        public Evento(String tipo, String origen, String datos) {
            this.tipo = tipo;
            this.origen = origen;
            this.datos = datos;
        }
    }

    private List<Evento> leerEventos() {
        List<Evento> eventos = new ArrayList<>();
        if (!Files.exists(archivo)) return eventos;
        try (BufferedReader r = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = r.readLine()) != null) {
                String[] p = linea.split(SEP, 4);
                if (p.length == 4) eventos.add(new Evento(p[1], p[2], p[3]));
            }
        } catch (IOException e) {
            logger.warn("NO SE PUDO LEER HISTORIAL: " + e.getMessage());
        }
        return eventos;
    }

    /**
     * Reproduce el historial en un hilo aparte, llamando a onEvento por
     * cada línea leída, con una pausa entre cada una.
     */
    public void reproducir(Consumer<Evento> onEvento, long pausaMs) {
        List<Evento> eventos = leerEventos();
        logger.info("REPRODUCIENDO HISTORIAL: " + eventos.size() + " eventos");
        new Thread(() -> {
            for (Evento ev : eventos) {
                onEvento.accept(ev);
                try {
                    Thread.sleep(pausaMs);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "reproduccion-historial").start();
    }
}
