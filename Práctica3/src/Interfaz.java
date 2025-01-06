import javax.swing.*;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.net.InetAddress;
import java.net.MulticastSocket;

class Interfaz extends JFrame {
    private JPanel contentPane;
    private JPanel panelTituloBotones;
    private JPanel panelTitulo;
    private JPanel panelBtnIzquierda;

    private JLabel titulo;
    private JButton btnMensajePrivado;
    private JButton btnEnviarArchivo;
    private JButton btnSalir;

    final static int WIDTH = 1000;
    final static int HEIGHT = 600;

    private static JLabel lblInicio;
    private static JPanel panelInicio;
    private static JTextField campoNombre;
    private static String nombreUsuario;

    private JTextArea textAreaChat;
    private JPanel panelEnviarMensaje;
    private JTextField textFieldMensaje;

    public Interfaz(String nombreUsuario, MulticastSocket socket, InetAddress grupo) {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, WIDTH, HEIGHT);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

        setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(15, 0));

        // Panel superior
		panelTituloBotones = new JPanel();
		contentPane.add(panelTituloBotones, BorderLayout.NORTH);
		panelTituloBotones.setLayout(new BorderLayout(0, 0));
		
        // Título
		panelTitulo = new JPanel();
		panelTituloBotones.add(panelTitulo, BorderLayout.NORTH);
		titulo = new JLabel("Sala de chat");
        titulo.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 48)); // NOI18N
		panelTitulo.add(titulo);

        // Botones izquierda
		panelBtnIzquierda = new JPanel();
		panelTituloBotones.add(panelBtnIzquierda, BorderLayout.WEST);
		btnMensajePrivado = new JButton("Enviar mensaje privado");
		panelBtnIzquierda.add(btnMensajePrivado);
		btnEnviarArchivo = new JButton("Enviar archivo");
		panelBtnIzquierda.add(btnEnviarArchivo);
        btnSalir = new JButton("Salir de la sala");
		panelBtnIzquierda.add(btnSalir);

        // Área para mostrar mensajes 
        textAreaChat = new JTextArea();
        textAreaChat.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textAreaChat);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Envio de mensajes
        panelEnviarMensaje = new JPanel();
        panelEnviarMensaje.setLayout(new BorderLayout());
        contentPane.add(panelEnviarMensaje, BorderLayout.SOUTH);
        textFieldMensaje = new JTextField();
        panelEnviarMensaje.add(textFieldMensaje, BorderLayout.CENTER);

        JButton btnEnviarMensaje = new JButton("Enviar");
        panelEnviarMensaje.add(btnEnviarMensaje, BorderLayout.EAST);

        btnSalir.addActionListener(e -> {
            Client.salirServidor(Client.nombreUsuario, Client.socketMulticast, Client.grupo);
            dispose();
        });
        btnEnviarMensaje.addActionListener(e -> {
            String mensaje = textFieldMensaje.getText().trim();
            if (!mensaje.isEmpty()) {
                Client.enviarMensajeGrupal(Client.nombreUsuario, mensaje, Client.socketMulticast);
                textFieldMensaje.setText("");
            }
        });
        // btnMensajePrivado.addActionListener(e -> {
        //     ventanaSeleccionarUsuario();
        // });

        setVisible(true);
    }

    public void mostrarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> textAreaChat.append(mensaje + "\n"));
    }

    // Trabajando en esta sección ...
    // private void ventanaSeleccionarUsuario() {
    //     JDialog dialog = new JDialog(this, "Seleccionar usuario", true);
    //     dialog.setSize(300, 400);
    //     dialog.setLayout(new BorderLayout());

    //     // Lista de usuarios activos
    //     JList<String> listaUsuarios = new JList<>();
    //     JScrollPane scrollPane = new JScrollPane(listaUsuarios);
    //     dialog.add(scrollPane, BorderLayout.CENTER);

    //     JButton btnAceptar = new JButton("Aceptar");

    //     btnAceptar.addActionListener(e -> {
    //         String usuarioSeleccionado = listaUsuarios.getSelectedValue();
    //         if (usuarioSeleccionado != null) {
    //             JOptionPane.showMessageDialog(this, "Mensaje privado a: " + usuarioSeleccionado);
    //             dialog.dispose();
    //         } else {
    //             JOptionPane.showMessageDialog(this, "Selecciona un usuario.");
    //         }
    //     });

    //     dialog.add(btnAceptar, BorderLayout.SOUTH);

    //     dialog.setLocationRelativeTo(this);
    //     dialog.setVisible(true);
    // }

    private static void ventanaInicio() {
        JFrame ventanaInicio = new JFrame("Inicio de Sesión");
        
        ventanaInicio.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventanaInicio.setSize(300, 200);
        ventanaInicio.setLocationRelativeTo(null);
        ventanaInicio.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        panelInicio = new JPanel();
        panelInicio.setLayout(new GridLayout(2,1,10,10));
        lblInicio = new JLabel("Ingresa tu nombre de usuario:");
        campoNombre = new JTextField();

        panelInicio.add(lblInicio);
        panelInicio.add(campoNombre);
        
        JButton btnConectar = new JButton("Conectar");
        btnConectar.setPreferredSize(new Dimension(100, 40));

        btnConectar.addActionListener(e -> {
            nombreUsuario = campoNombre.getText().trim();
            if (!nombreUsuario.isEmpty()) {
                Client.nombreUsuario = nombreUsuario;
                Client.main(null);
                
                SwingUtilities.invokeLater(() -> {
                    ventanaInicio.dispose();
                    Interfaz interfaz = new Interfaz(Client.nombreUsuario, Client.socketMulticast, Client.grupo);
                    new Thread(() -> Client.recibirMensajesMulticast(Client.socketMulticast, interfaz)).start();
                    new Thread(() -> Client.recibirMensajesUnicast(Client.socketUnicast, interfaz)).start();
                });
            } else {
                JOptionPane.showMessageDialog(ventanaInicio, "Por favor, ingresa un nombre de usuario válido.");
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        ventanaInicio.add(panelInicio, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        ventanaInicio.add(btnConectar, gbc);
        ventanaInicio.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ventanaInicio();
        });
    }
}
