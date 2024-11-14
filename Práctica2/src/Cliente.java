import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JEditorPane;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

import java.util.Arrays;

public class Cliente {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA = 500;

    //final static String fileName = "./archivo.txt";
    static String nombreArchivo = "";
    static String rutaArchivo = "";
    static Path rutaDirectorio;

    static Window ventana;
    
    public static void main(String[] args){
        try{
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramSocket socket = new DatagramSocket();
            InetAddress direccion = InetAddress.getByName(dir_host);

            // ------------------------------------------------------------------------
            //                                  HANDSHAKE
            // ------------------------------------------------------------------------
            /*
            // Enviar SYN
            String SYN = "SYN";
            byte[] SYNBytes = SYN.getBytes();
            outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
            outStream.write(SYNBytes);              // SYN
            outStream.flush();
            
            byte[] bufferSYN = byteOut.toByteArray();
            DatagramPacket packet = new DatagramPacket(bufferSYN, bufferSYN.length, direccion, PORT);
            System.out.println("\033[93mEnviando "+SYN+"\033[0m");
            socket.send(packet);
            byteOut.reset();

            // Recibir SYN - ACK
            byte[] buffer = new byte[TAM_BUFFER];
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            byteIn = new ByteArrayInputStream(packet.getData());
            inStream = new DataInputStream(byteIn);

            int SYNTAM = inStream.readInt();
            bufferSYN = new byte[SYNTAM];
            inStream.read(bufferSYN);
            SYN = new String(bufferSYN);
            if(!SYN.equals("SYN - ACK")) {
                //throw(new Exception());
            }
            System.out.println("\033[93mRecibido "+SYN+"\033[0m");

            // Enviar ACK
            SYN = SYN.substring(6, 9);
            SYNBytes = SYN.getBytes();
            outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
            outStream.write(SYNBytes);              // SYN
            outStream.flush();
            
            bufferSYN = byteOut.toByteArray();
            packet = new DatagramPacket(bufferSYN, bufferSYN.length, direccion, PORT);
            System.out.println("\033[93mEnviando "+SYN+"\033[0m");
            socket.send(packet);
            byteOut.reset();
*/
            rutaDirectorio = Paths.get("./");
            ventana = new Window(rutaDirectorio);
            //subirArchivo(socket, direccion);

            outStream.close();
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        
    }

    static void subirArchivo(DatagramSocket socket, InetAddress direccion) {

        try{
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            // Crear el hilo para mantener viva la conexión
            Thread hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -5, TIEMPO_ESPERA));
            hiloConexion.start();

            // ------------------------------------------------------------------------
            //                                   DATOS
            // ------------------------------------------------------------------------

            DatagramPacket packet;

            JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            int returnValue = jfc.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = jfc.getSelectedFile();
                rutaArchivo = selectedFile.getAbsolutePath();
                nombreArchivo = selectedFile.getName();
            }
            hiloConexion.interrupt();

            Path path = Paths.get(rutaArchivo);
            byte[] file = Files.readAllBytes(path);
            byte[] fileNameBytes = nombreArchivo.getBytes();

            int tam = TAM_PAQUETE;
            int PAQUETES_COMPLETOS = (int) file.length/TAM_PAQUETE;
            int TOTAL_PAQUETES = (int) file.length%TAM_PAQUETE == 0 ? PAQUETES_COMPLETOS : PAQUETES_COMPLETOS+1;
            int n_sobrantes = (int) file.length % TAM_PAQUETE;

            int start = 0; // Apuntador al inicio de la ventana
            int apuntador = 0; // Apuntador al paquete que se va a mandar
            while (start < TOTAL_PAQUETES) {
                // Enviar paquetes en la ventana
                while (apuntador < start + TAM_VENTANA && apuntador < TOTAL_PAQUETES) {
                    byte[] btmp;
                    // Si es el paquete final (y es más pequeño que el tamaño de paquete)
                    if(apuntador == PAQUETES_COMPLETOS)
                        btmp = Arrays.copyOfRange(file, apuntador*tam, apuntador*tam + n_sobrantes);
                    else
                        btmp = Arrays.copyOfRange(file, apuntador*tam, apuntador*tam + tam);
                    outStream.writeInt(apuntador);              // Número de paquete
                    outStream.writeInt(TOTAL_PAQUETES);         // Total de paquetes
                    outStream.writeInt(fileNameBytes.length);   // Tamaño del nombre del archivo
                    outStream.write(fileNameBytes);             // Nombre del archivo
                    outStream.writeInt(btmp.length);            // Tamaño de los datos
                    outStream.write(btmp);                      // Datos
                    outStream.flush();

                    // Enviar paquete
                    byte[] bufferOut = byteOut.toByteArray();
                    packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
                    /*System.out.println("Enviando el paquete "+apuntador+" con el mensaje: ");
                    System.out.println(new String(btmp));*/
                    socket.send(packet);
                    byteOut.reset();
                        apuntador++;
                }

                try {
                    // Recibir el ACK
                    socket.setSoTimeout(TIEMPO_ESPERA);
                    byte[] buffer = new byte[TAM_BUFFER];
                    packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byteIn = new ByteArrayInputStream(packet.getData());
                    inStream = new DataInputStream(byteIn);
                    int n = inStream.readInt();
                    System.out.println("\033[95mACK: \033[0m"+n);
                    System.out.flush();
                    if (n >= start)
                        start = n + 1; // Mover el inicio de la ventana

                } catch (SocketTimeoutException e) {
                    System.out.println("\033[31mTIMEOUT: retransmitiendo desde el paquete " + start+"\033[0m");
                    System.out.flush();
                    apuntador = start; // Empezar a transmitir los paquetes desde el inicio de la ventana
                }
            }
            System.out.println("\033[94mEnvío exitoso del archivo "+nombreArchivo+".\033[0m");
            System.out.flush();

            outStream.close();
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    static void borrarArchivo(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramPacket packet;

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}

class Window extends JFrame {
    private JPanel contentPane;
    private JPanel panelTituloBotones;
    private JPanel panelTitulo;
    private JPanel panelBtnIzquierda;
    private JPanel panelBtnDerecha;

    private JPanel panelDirectorio;
    private JPanel panelBtnDirectorios;

    private JLabel titulo;
    private JEditorPane editorPane;
    private JButton btnSubir;
    private JButton btnBajar;
    private JButton btnCrear;
    private JButton btnBorrar;

    private JButton btnAvanzar;
    private JButton btnRegresar;
    private JLabel directorio;

    final static int WIDTH = 800;
    final static int HEIGHT = 500;

    final static Dimension tamBtn = new Dimension(250, 150);

    public Window(Path rutaDirectorio) {
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
		titulo = new JLabel("Nube");
        titulo.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 48)); // NOI18N
		panelTitulo.add(titulo);
		
        // Botones izquierda
		panelBtnIzquierda = new JPanel();
		panelTituloBotones.add(panelBtnIzquierda, BorderLayout.WEST);
		btnSubir = new JButton("Subir Archivo");
		panelBtnIzquierda.add(btnSubir);
		btnBajar = new JButton("Bajar Archivo");
		panelBtnIzquierda.add(btnBajar);
		btnCrear = new JButton("Crear carpeta");
		panelBtnIzquierda.add(btnCrear);
		
        // Botones derecha
		panelBtnDerecha = new JPanel();
		panelTituloBotones.add(panelBtnDerecha, BorderLayout.EAST);
		btnBorrar = new JButton("Borrar archivo/carpeta");
		panelBtnDerecha.add(btnBorrar);

        // panelBtnDirectorios
        panelDirectorio = new JPanel();
        panelTituloBotones.add(panelDirectorio, BorderLayout.SOUTH);
        panelDirectorio.setLayout(new BorderLayout(0, 0));
		
        panelBtnDirectorios = new JPanel();
        panelDirectorio.add(panelBtnDirectorios, BorderLayout.EAST);

        btnAvanzar = new JButton("Entrar a directorio");
        panelBtnDirectorios.add(btnAvanzar);
        btnRegresar = new JButton("Subir un directorio");
        panelBtnDirectorios.add(btnRegresar);
        
        directorio = new JLabel(rutaDirectorio.toString());
        directorio.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 12)); // NOI18N
        panelDirectorio.add(directorio, BorderLayout.WEST);
		
        // Texto
        int epWidth = (2*WIDTH)/3;
        int epHeight = (2*HEIGHT)/3-50;
        editorPane = new JEditorPane();
        editorPane.setContentType("text/html"); // Puede ser texto simple o HTML
        editorPane.setText("<html><h1>Bienvenido</h1><p>Este es un ejemplo de JEditorPane.</p></html>");
        editorPane.setPreferredSize(new Dimension(epWidth, epHeight));
        editorPane.setEditable(false);

        // Colocar el JEditorPane en un JScrollPane
        JScrollPane scrollPane = new JScrollPane(editorPane);
        contentPane.add(scrollPane, BorderLayout.SOUTH);

        //btnSubir.set

        setVisible(true);
    }

    public void actualizarDirectorio() {

    }
}