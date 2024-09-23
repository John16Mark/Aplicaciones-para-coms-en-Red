import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import java.awt.event.MouseEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;

public class ButtonGridFrame extends JFrame {
    private int rows;
    private int cols;
    private JPanel contentPane;

    public ButtonGridFrame(int n, int m) {
        this.rows = n;
        this.cols = m;
		int width = m*20;
        int height = n*20;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, width+50, height+100);
		contentPane = new JPanel();
		//contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_titulo = new JPanel();
		contentPane.add(panel_titulo, BorderLayout.NORTH);
        panel_titulo.setBackground(new Color(30,127,239));
		JLabel lblNewLabel = new JLabel("BUSCAMINAS");
		panel_titulo.add(lblNewLabel);

        JPanel panel_tablero = new JPanel();
        contentPane.add(panel_tablero, BorderLayout.SOUTH);
        panel_tablero.setBackground(new Color(127,30,239));
        panel_tablero.setLayout(new GridLayout(n, m));

        // Establecer el layout como GridLayout con n filas y m columnas
        // Crear y añadir los botones con sus eventos
        for (int i = 0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                JButton button = new JButton("");
                button.setPreferredSize(new Dimension(20, 20));
                /*int i_temp = i;
                int j_temp = j;
                // Añadir un MouseListener a cada botón para detectar clics
                button.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        // Detectar clic izquierdo
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            onLeftClick(button, i_temp, j_temp);
                        }
                        // Detectar clic derecho
                        else if (SwingUtilities.isRightMouseButton(e)) {
                            onRightClick(button, i_temp, j_temp);
                        }
                    }
                });*/
                panel_tablero.add(button);
            }
        }

        // Ajustar el tamaño de la ventana y hacerla visible
        //pack();
        setVisible(true);
    }

    // Función a ejecutar con clic izquierdo
    private void onLeftClick(JButton button, int x, int y) {
        System.out.println("Clic izquierdo en: " + button.getText());
        // Aquí puedes colocar la lógica que desees
        button.setText("Left Clicked!");
    }

    // Función a ejecutar con clic derecho
    private void onRightClick(JButton button, int x, int y) {
        System.out.println("Clic derecho en: " + button.getText());
        // Aquí puedes colocar la lógica que desees
        button.setText("Right Clicked!");
    }

}