package pizarra.en.red.gui;

import javax.swing.*;

/**
 * Encapsula el pequeño diálogo modal "Conectando..." / "Esperando
 * conexión...". Es lo único que ConexionControlador necesita pedirle a
 * la ventana durante el proceso de conectar -- aislarlo en su propia
 * clase evita que VentanaDibujo tenga que manejar directamente el JDialog
 * (crearlo, guardarlo en un campo, acordarse de cerrarlo).
 */
public class DialogoEspera {

    private final JFrame propietario;
    private JDialog dialogo;

    public DialogoEspera(JFrame propietario) {
        this.propietario = propietario;
    }

    public void mostrar(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            dialogo = new JDialog(propietario, "Conexión", false);
            JLabel label = new JLabel(mensaje);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            dialogo.add(label);
            dialogo.setSize(250, 100);
            dialogo.setLocationRelativeTo(propietario);
            dialogo.setVisible(true);
        });
    }

    public void cerrar() {
        SwingUtilities.invokeLater(() -> {
            if (dialogo != null) {
                dialogo.dispose();
                dialogo = null;
            }
        });
    }
}
