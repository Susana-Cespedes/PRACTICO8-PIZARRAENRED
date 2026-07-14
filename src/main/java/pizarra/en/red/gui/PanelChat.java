package pizarra.en.red.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

/**
 * Panel de chat: la caja de texto con los mensajes, el campo para
 * escribir y el botón Enviar. No sabe nada de red ni de historial --
 * solo sabe mostrar líneas y avisar (con el callback que le pasen en el
 * constructor) cuando el usuario quiere enviar un mensaje. Mismo espíritu
 * que PanelDibujo: un componente Swing autocontenido.
 */
public class PanelChat extends JPanel {

    private final JTextArea txtChat;
    private final JTextField txtMensaje;

    public PanelChat(Consumer<String> alEnviar) {
        super(new BorderLayout(5, 5));

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
                alEnviar.accept(texto);
                txtMensaje.setText("");
            }
        };
        btnEnviar.addActionListener(enviarAccion);
        txtMensaje.addActionListener(enviarAccion); // Enter también envía

        envio.add(txtMensaje, BorderLayout.CENTER);
        envio.add(btnEnviar, BorderLayout.EAST);

        add(scroll, BorderLayout.CENTER);
        add(envio, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(240, 0));
    }

    public void agregarPropio(String texto) {
        agregarLinea("Yo: " + texto);
    }

    public void agregarRemoto(String texto) {
        agregarLinea("Otro: " + texto);
    }

    public void agregarHistorial(String quien, String texto) {
        agregarLinea("[Historial] " + quien + ": " + texto);
    }

    public void agregarAviso(String texto) {
        agregarLinea(texto);
    }

    public void limpiar() {
        txtChat.setText("");
    }

    private void agregarLinea(String linea) {
        txtChat.append(linea + "\n");
    }
}
