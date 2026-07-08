package pizarra.en.red.objetos;

import editar.imagenes.objects.VerySimpleImageWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Base64;

/**
 * Figura especial: una imagen JPG o PNG.
 *
 * Refactor: en vez de guardar los bytes crudos del archivo (jpg/png
 * comprimidos), se guarda la imagen como lo hace VerySimpleImageWriter en
 * el práctico de Agrandar/Achicar -- ancho, alto y un arreglo de enteros
 * con el color de cada píxel (formato ARGB de BufferedImage.getRGB). Así
 * las dos partes del curso usan la misma representación de imagen.
 *
 * Para viajar por el protocolo de texto (una línea por figura), ese
 * arreglo de enteros se convierte a bytes y se codifica en Base64 -- es
 * la misma idea que ya se usaba, solo que ahora lo que se codifica son
 * los píxeles en vez del archivo comprimido.
 */
public class Imagen extends Figura {

    private final int ancho;
    private final int alto;
    private final int[] puntos;

    private transient BufferedImage cache; // se arma una sola vez, no en cada repintado

    public Imagen(int x, int y, int ancho, int alto, int[] puntos) {
        super(x, y);
        this.ancho = ancho;
        this.alto = alto;
        this.puntos = puntos;
    }

    /**
     * Lee un archivo JPG/PNG del disco y lo convierte a la representación
     * de píxeles usando VerySimpleImageWriter, igual que ImagenModelo.
     */
    public static Imagen desdeArchivo(int x, int y, File archivo) throws IOException {
        BufferedImage bi = ImageIO.read(archivo);
        if (bi == null) {
            throw new IOException("El archivo no es una imagen válida: " + archivo.getName());
        }
        VerySimpleImageWriter vsiw = new VerySimpleImageWriter(bi);
        return new Imagen(x, y, vsiw.getAncho(), vsiw.getAlto(), vsiw.getPuntos());
    }

    public int getAncho()    { return ancho; }
    public int getAlto()     { return alto; }
    public int[] getPuntos() { return puntos; }

    /**
     * Reconstruye la imagen dibujable a partir de los píxeles. Es el mismo
     * mecanismo que usa VerySimpleImageWriter.escribirArchivo (BufferedImage
     * + setRGB), pero sin pasar por disco -- acá solo hace falta mostrarla
     * en el panel, no guardarla.
     */
    public BufferedImage obtenerImagen() {
        if (cache == null) {
            cache = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            cache.setRGB(0, 0, ancho, alto, puntos, 0, ancho);
        }
        return cache;
    }

    @Override
    public String serializar() {
        ByteBuffer bb = ByteBuffer.allocate(puntos.length * Integer.BYTES);
        for (int p : puntos) bb.putInt(p);
        String base64 = Base64.getEncoder().encodeToString(bb.array());
        return "IMAGEN " + x + " " + y + " " + ancho + " " + alto + " " + base64;
    }

    /**
     * Reconstruye una Imagen a partir de los campos ya separados de la
     * línea serializada: [ "IMAGEN", x, y, ancho, alto, base64 ].
     */
    public static Imagen deserializar(String[] campos) {
        int x     = Integer.parseInt(campos[1]);
        int y     = Integer.parseInt(campos[2]);
        int ancho = Integer.parseInt(campos[3]);
        int alto  = Integer.parseInt(campos[4]);
        byte[] bytes = Base64.getDecoder().decode(campos[5]);

        IntBuffer ib = ByteBuffer.wrap(bytes).asIntBuffer();
        int[] puntos = new int[ib.remaining()];
        ib.get(puntos);

        return new Imagen(x, y, ancho, alto, puntos);
    }
}
