import javax.swing.*;
import java.awt.*;
import javax.swing.border.EmptyBorder;
import java.nio.file.Files;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.File;

class Interfaz extends JFrame {
    private JPanel contentPane;
    private JPanel panelTituloBotones;
    private JPanel panelTitulo;
    private JPanel panelBtnIzquierda;

    private JLabel titulo;
    private JButton btnMensajePrivado;
    private JButton btnEnviarArchivo;
    private JButton btnSalir;
    private JButton btnVerArchivos;

    final static int WIDTH = 500;
    final static int HEIGHT = 300;

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
        titulo.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 48));
		panelTitulo.add(titulo);

        // Botones izquierda 
        panelBtnIzquierda = new JPanel();
        panelBtnIzquierda.setLayout(new BoxLayout(panelBtnIzquierda, BoxLayout.Y_AXIS)); // Disposición vertical
        panelTituloBotones.add(panelBtnIzquierda, BorderLayout.WEST);

        // Panel para los tres primeros botones
        JPanel panelFilaSuperior = new JPanel();
        panelFilaSuperior.setLayout(new FlowLayout(FlowLayout.LEFT)); // Alineación a la izquierda
        btnMensajePrivado = new JButton("Enviar mensaje privado");
        panelFilaSuperior.add(btnMensajePrivado);
        btnEnviarArchivo = new JButton("Enviar archivo");
        panelFilaSuperior.add(btnEnviarArchivo);
        btnSalir = new JButton("Salir de la sala");
        panelFilaSuperior.add(btnSalir);
        panelBtnIzquierda.add(panelFilaSuperior);

        // Panel para el botón "Ver archivos"
        JPanel panelFilaInferior = new JPanel();
        panelFilaInferior.setLayout(new FlowLayout(FlowLayout.LEFT)); // Alineación a la izquierda
        btnVerArchivos = new JButton("Ver archivos");
        panelFilaInferior.add(btnVerArchivos);
        panelBtnIzquierda.add(panelFilaInferior);

        // Panel principal
        JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(300);
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

        JButton btnEmoji = new JButton("😀");
        panelEnviarMensaje.add(btnEmoji, BorderLayout.WEST);

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
            JFileChooser jfc = new JFileChooser();
            jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnValue = jfc.showOpenDialog(this);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = jfc.getSelectedFile();
                Client.enviarSolicitudArchivo(Client.nombreUsuario, selectedFile, socket);
            } else {
                JOptionPane.showMessageDialog(this, "No se seleccionó ningún archivo.", "Advertencia", JOptionPane.WARNING_MESSAGE);
            }
        });
        /* ---------------------------------------------------------------------------------------
         *                              ENVIAR ARCHIVO
         * --------------------------------------------------------------------------------------- */
        btnEmoji.addActionListener(e -> {
            String[] emojis = {"😀", "😂", "❤️", "👍", "😢", "🔥", "🎉", "💡"};
            String emojiSeleccionado = (String) JOptionPane.showInputDialog(
                this,
                "Selecciona un emoji:",
                "Emojis",
                JOptionPane.PLAIN_MESSAGE,
                null,
                emojis,
                emojis[0]
            );
            if (emojiSeleccionado != null) {
                textFieldMensaje.setText(textFieldMensaje.getText() + emojiSeleccionado);
            }
        });
        /* ---------------------------------------------------------------------------------------
         *                             VER ARCHIVOS DEL USUARIO
         * --------------------------------------------------------------------------------------- */
        btnVerArchivos.addActionListener(e -> {
            String rutaBase = "ArchivosUsuarios";
            File carpetaUsuario = new File(rutaBase, nombreUsuario);

            if (!carpetaUsuario.exists() || !carpetaUsuario.isDirectory()) {
                JOptionPane.showMessageDialog(this, "No tienes archivos guardados " + nombreUsuario, "Carpeta no encontrada", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File[] archivos = carpetaUsuario.listFiles();
            if (archivos == null || archivos.length == 0) {
                JOptionPane.showMessageDialog(this, "No hay archivos disponibles para mostrar", "Carpeta vacía", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            StringBuilder listaArchivos = new StringBuilder("Archivos disponibles:\n");
            for (File archivo : archivos) {
                listaArchivos.append(archivo.getName()).append("\n");
            }

            JOptionPane.showMessageDialog(this, listaArchivos.toString(), "Archivos del usuario: " + nombreUsuario, JOptionPane.INFORMATION_MESSAGE);

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
