import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;

import java.io.IOException;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

public class Ventana extends JFrame {
    private int rows;
    private int cols;
    private JPanel contentPane;
    private final int btnSize = 25;
    private JButton[][] buttons;
    private char[][] tablero;

    private Color fondo = new Color(139, 199, 239);
    private Color btnBordeDefault = new Color(122, 138, 153);
    private Color btnNada = new Color(173, 173, 173);
    private Color btn1 = new Color(2, 0, 255);
    private Color btn2 = new Color(2, 130, 0);
    private Color btn3 = new Color(254, 0 ,0);
    private Color btn4 = new Color(1, 0, 132);
    private Color btn5 = new Color(134, 0 , 1);
    private Color btn6 = new Color(3, 130, 128);
    private Color btn7 = new Color(133, 0 , 131);
    private Color btn8 = new Color(118, 118, 118);

    private Font fontMS;

    private JLabel lblNewLabel;

    public Ventana(int n, int m) {
        this.rows = n;
        this.cols = m;
        this.buttons = new JButton[n][m];
        this.tablero = new char[n][m];
		int width = m*btnSize;
        int height = n*btnSize;

        try {
			// load a custom font in your project folder
			fontMS = Font.createFont(Font.TRUETYPE_FONT, new File("fonts/mine-sweeper.ttf")).deriveFont(15f);	
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File("fonts/mine-sweeper.ttf")));			
		} catch(IOException | FontFormatException e) {}
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, width+50, height+100);
		contentPane = new JPanel();

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		JPanel panel_titulo = new JPanel();
		contentPane.add(panel_titulo, BorderLayout.NORTH);
        panel_titulo.setBackground(fondo);
		lblNewLabel = new JLabel("BUSCAMINAS");
		panel_titulo.add(lblNewLabel);

        JPanel panel_tablero = new JPanel();
        contentPane.add(panel_tablero, BorderLayout.CENTER);
        panel_tablero.setBackground(fondo);

        // GridLayout para los botones
        GridBagLayout gbl = new GridBagLayout();
        panel_tablero.setLayout(gbl);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE; 
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.insets = new Insets(0, 0, 0, 0);

        // Crear botones
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                JButton button = new JButton("");
                button.setPreferredSize(new Dimension(btnSize, btnSize));
                buttons[i][j] = button; 

                int i_temp = i;
                int j_temp = j;
                button.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if(buttons[i_temp][j_temp].isEnabled()) {
                            if (SwingUtilities.isLeftMouseButton(e))
                                clicIzq(button, i_temp, j_temp);
                            else if (SwingUtilities.isRightMouseButton(e)) 
                                clicDer(button, i_temp, j_temp);
                        }
                    }
                });

                gbc.gridx = j;
                gbc.gridy = i;
                panel_tablero.add(button, gbc);
            }
        }

        setVisible(true);
    }

    public void setTitulo(String s) {
        lblNewLabel.setText(s);
    }

    public void actualizar(char[][] tablero, int n, int m) {
        this.tablero = tablero;
        for(int i=0; i<n; i++) {
            for(int j=0; j<m; j++) {
                char casilla = this.tablero[i][j];
                if((casilla >= '1' && casilla <= '8') || casilla == ' ')
                    botonNada(i, j, casilla);
                else if(casilla == 'i')
                    botonBandera(i, j, true);
                else if(casilla == 'O')
                    botonMina(i,j);
                else if(casilla == 'X')
                    botonBanderaMina(i, j);
                else
                    botonBandera(i, j, false);
            }
        }
    }

    public void fin() {
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                JButton boton = buttons[i][j];
                for (MouseListener listener : boton.getMouseListeners())
                    boton.removeMouseListener(listener);
            }
        }
    }

    public void botonBandera(int fila, int col, boolean poner) {
        JButton boton = buttons[fila][col];
        if(poner){
            ImageIcon icon = new ImageIcon("img/bandera.png");
            Image image = icon.getImage();
            Image resizedImage = image.getScaledInstance(btnSize, btnSize, java.awt.Image.SCALE_SMOOTH); // Ajusta el tamaño según sea necesario
            ImageIcon resizedIcon = new ImageIcon(resizedImage);
            boton.setIcon(resizedIcon);
        } else
            boton.setIcon(null);
    }

    public void botonBanderaMina(int fila, int col) {
        JButton boton = buttons[fila][col];
        ImageIcon icon = new ImageIcon("img/minabandera.png");
        Image image = icon.getImage();
        Image resizedImage = image.getScaledInstance(btnSize, btnSize, java.awt.Image.SCALE_SMOOTH); // Ajusta el tamaño según sea necesario
        ImageIcon resizedIcon = new ImageIcon(resizedImage);
        boton.setIcon(resizedIcon);
    }

    public void botonMina(int fila, int col) {
        JButton boton = buttons[fila][col];
        ImageIcon icon = new ImageIcon("img/mina.png");
        Image image = icon.getImage();
        Image resizedImage = image.getScaledInstance(btnSize, btnSize, java.awt.Image.SCALE_SMOOTH); // Ajusta el tamaño según sea necesario
        ImageIcon resizedIcon = new ImageIcon(resizedImage);
        boton.setIcon(resizedIcon);
    }

    public void botonNada(int fila, int col, char num) {
        if (fila >= 0 && fila < rows && col >= 0 && col < cols) {
            JButton boton = buttons[fila][col];
            boton.setFocusable(false);
            boton.setBackground(btnNada);
            boton.setBorder(BorderFactory.createLineBorder(btnBordeDefault, 1));
            if(num != ' ')
                boton.setText(""+num);
            boton.setFont(fontMS);

            switch(num) {
                case '1': boton.setForeground(btn1); break;
                case '2': boton.setForeground(btn2); break;
                case '3': boton.setForeground(btn3); break;
                case '4': boton.setForeground(btn4); break;
                case '5': boton.setForeground(btn5); break;
                case '6': boton.setForeground(btn6); break;
                case '7': boton.setForeground(btn7); break;
                case '8': boton.setForeground(btn8); break;
                default:  boton.setForeground(new Color(0, 255, 255));
            }
            
            for (MouseListener listener : boton.getMouseListeners())
                boton.removeMouseListener(listener);
        } else {
            System.out.println("Coordenadas fuera de rango");
        }
    }

    private void clicIzq(JButton button, int x, int y) {
        if(tablero[x][y] != 'i')
            ClienteUI.mandarDatos(1, x, y);
    }

    private void clicDer(JButton button, int x, int y) {
        if(tablero[x][y] == 'i')
            ClienteUI.mandarDatos(3, x, y);
        else
            ClienteUI.mandarDatos(2, x, y);
    }

}
