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

    private DefaultListModel<String> modeloUsuarios;

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

        // Panel principal
        JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(600);
        contentPane.add(splitPane, BorderLayout.CENTER);

        // Área para mostrar mensajes 
        JPanel panelChat = new JPanel(new BorderLayout());
        textAreaChat = new JTextArea();
        textAreaChat.setEditable(false);
        JScrollPane scrollPaneChat = new JScrollPane(textAreaChat);
        panelChat.add(scrollPaneChat, BorderLayout.CENTER);
        splitPane.setLeftComponent(panelChat);

        // Área de usuarios activos
        JPanel panelUsuarios = new JPanel(new BorderLayout());
        JLabel lblUsuariosActivos = new JLabel("Usuarios activos");
        lblUsuariosActivos.setHorizontalAlignment(SwingConstants.CENTER);
        panelUsuarios.add(lblUsuariosActivos, BorderLayout.NORTH);

        modeloUsuarios = new DefaultListModel<>();
        for (String usuario: Client.listaUsuariosActivos) {
            if (!usuario.equals(Client.nombreUsuario)) {
                modeloUsuarios.addElement(usuario);
            }
        }

        JList<String> listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(listaUsuarios);
        panelUsuarios.add(scrollPane, BorderLayout.CENTER);

        splitPane.setRightComponent(panelUsuarios);

        // Botón para el envío de mensajes
        panelEnviarMensaje = new JPanel();
        panelEnviarMensaje.setLayout(new BorderLayout());
        contentPane.add(panelEnviarMensaje, BorderLayout.SOUTH);
        textFieldMensaje = new JTextField();
        panelEnviarMensaje.add(textFieldMensaje, BorderLayout.CENTER);

        JButton btnEnviarMensaje = new JButton("Enviar");
        panelEnviarMensaje.add(btnEnviarMensaje, BorderLayout.EAST);

        // Acciones para los botones
        /* ---------------------------------------------------------------------------------------
          *                                  SALIR DEL SERVIDOR
          * --------------------------------------------------------------------------------------- */
        btnSalir.addActionListener(e -> {
            Client.salirServidor(Client.nombreUsuario, Client.socketMulticast, Client.grupo);
            dispose();
        });
        /* ---------------------------------------------------------------------------------------
          *                               ENVIAR MENSAJE GRUPAL
          * --------------------------------------------------------------------------------------- */
        btnEnviarMensaje.addActionListener(e -> {
            String mensaje = textFieldMensaje.getText().trim();
            if (!mensaje.isEmpty()) {
                Client.enviarMensajeGrupal(Client.nombreUsuario, mensaje, Client.socketMulticast);
                textFieldMensaje.setText("");
            }
        });
        /* ---------------------------------------------------------------------------------------
          *                              ENVIAR MENSAJE PRIVADO
          * --------------------------------------------------------------------------------------- */
        btnMensajePrivado.addActionListener(e -> {
            String mensaje = textFieldMensaje.getText().trim();
            String usuarioSeleccionado = listaUsuarios.getSelectedValue();
            if (usuarioSeleccionado == null) {
                JOptionPane.showMessageDialog(this, "Selecciona un usuario para mandar un mensaje privado", "Error", JOptionPane.WARNING_MESSAGE);
            }else if(!mensaje.isEmpty()){
                Client.enviarMensajePrivado(Client.nombreUsuario, mensaje, usuarioSeleccionado, socket);
                textFieldMensaje.setText("");
                textAreaChat.append("Mensaje privado a " + usuarioSeleccionado + ": " + mensaje + "\n");
            }
        });
        /* ---------------------------------------------------------------------------------------
          *                              ENVIAR ARCHIVO
          * --------------------------------------------------------------------------------------- */
        btnEnviarArchivo.addActionListener(e -> {
            Client.enviarArchivo(Client.nombreUsuario, socket);
            textAreaChat.append("Se ha enviado un archivo de parte de " + Client.nombreUsuario);
        });

        setVisible(true);
    }

    public void actualizarListaUsuarios() {
        modeloUsuarios.clear();
        for (String usuario: Client.listaUsuariosActivos) {
            if (!usuario.equals(Client.nombreUsuario)) {
                modeloUsuarios.addElement(usuario);
            }
        }
    }

    public void mostrarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> textAreaChat.append(mensaje + "\n"));
    }

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
