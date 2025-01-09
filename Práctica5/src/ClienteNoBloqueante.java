import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

public class ClienteNoBloqueante {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    enum Estado {
        ESPERANDO_SYN_ACK,
        RECIBIENDO_DIRECTORIO,
        CONEXION_ESTABLECIDA
    }

    static Estado estado = Estado.ESPERANDO_SYN_ACK;

    static Path rutaDirectorio;

    static WindowNoBloqueante ventana;

    public static void main(String[] args) throws Exception {
        
        // Canal de datagrama no bloqueante
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(InetAddress.getByName(dir_host), PORT));

        ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream outStream = new DataOutputStream(byteOut);

        // Enviar SYN
        String SYN = "SYN";
        byte[] SYN_bytes = SYN.getBytes();
        outStream.writeInt(SYN_bytes.length);
        outStream.write(SYN_bytes);
        outStream.flush();
        buffer.put(byteOut.toByteArray());
        buffer.flip();
        channel.write(buffer);
        System.out.println("\033[93mEnviando " + SYN + "\033[0m");
        byteOut.reset();

        DataInputStream inStream;
        while (true) {
            buffer.clear();
            channel.read(buffer);
            buffer.flip();

            switch (estado) {
                case ESPERANDO_SYN_ACK:
                    // Recibir SYN-ACK
                    inStream = new DataInputStream(new ByteArrayInputStream(buffer.array(), 0, buffer.limit()));
                    if (buffer.limit() == 0) continue; // Esperar más datos

                    int SYNTAM = inStream.readInt();
                    byte[] bufferSYN = new byte[SYNTAM];
                    inStream.read(bufferSYN);
                    SYN = new String(bufferSYN);
                    if (!SYN.equals("SYN - ACK")) {
                        throw new Exception("\033[31mError al establecer conexión.\033[0m");
                    }
                    System.out.println("\033[93mRecibido " + SYN + "\033[0m");

                    // Enviar ACK
                    SYN = "ACK";
                    outStream.writeInt(SYN.length());
                    outStream.write(SYN.getBytes());
                    outStream.flush();
                    buffer.clear();
                    buffer.put(byteOut.toByteArray());
                    buffer.flip();
                    channel.write(buffer);
                    System.out.println("\033[93mEnviando " + SYN + "\033[0m");
                    byteOut.reset();

                    rutaDirectorio = Paths.get("./");
                    ventana = new WindowNoBloqueante(channel, rutaDirectorio);
                    estado = Estado.RECIBIENDO_DIRECTORIO;
                    break;

                case RECIBIENDO_DIRECTORIO:
                    // Recibir contenido del directorio
                    inStream = new DataInputStream(new ByteArrayInputStream(buffer.array(), 0, buffer.limit()));
                    if (buffer.limit() == 0) continue; // Esperar más datos

                    System.out.println("\033[95m -- RECIBIR DIRECTORIO --\033[0m");
                    int dirTAM = inStream.readInt();
                    byte[] bufferdir = new byte[dirTAM];
                    inStream.read(bufferdir);
                    String directorio = new String(bufferdir);

                    int contenidoTAM = inStream.readInt();
                    byte[] bufferContenido = new byte[contenidoTAM];
                    inStream.read(bufferContenido);
                    String contenido = new String(bufferContenido);

                    System.out.println("\033[94mContenido recibido:\033[0m");
                    System.out.println("\033[93mNombre del directorio: " + directorio + "\033[0m");
                    System.out.println("\033[93mContenido del directorio: " + contenido + "\033[0m");

                    ventana.actualizarDirectorio(directorio, contenido);
                    estado = Estado.CONEXION_ESTABLECIDA;
                    break;

                case CONEXION_ESTABLECIDA:
                    // Aquí manejarás el envío/recepción de otros datos
                    //System.out.println("\033[92mConexión establecida. Listo para enviar/recibir más datos.\033[0m");
                    break;

                default:
                    throw new IllegalStateException("\033[31mEstado desconocido\033[0m");
            }
        }
    }

    // ------------------------------------------------------------------------
    //                            AVANZAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void avanzarDirectorio(DatagramChannel channel) {
        try {
            System.out.println("\n\033[95m -- AVANZAR DIRECTORIO --\033[0m");

            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            String path = JOptionPane.showInputDialog("Nombre del nuevo directorio");
            if(path == null)
                return;

            // Enviar datos
            byte[] path_bytes = path.getBytes();
            outStream.writeInt(-2);
            outStream.writeInt(path_bytes.length);
            outStream.write(path_bytes);
            outStream.flush();
            buffer.put(byteOut.toByteArray());
            buffer.flip();
            channel.write(buffer);
            byteOut.reset();
            
            System.out.println("\033[94mContenido enviado:\033[0m");
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-2));
            System.out.println("\033[93mLongitud del directorio: \033[0m"+path_bytes.length);
            System.out.println("\033[93mNombre del directorio: \033[0m"+new String(path_bytes));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             REGRESAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void regresarDirectorio(DatagramChannel channel) {
        try {
            System.out.println("\n\033[95m -- REGRESAR DIRECTORIO --\033[0m");

            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Enviar datos
            outStream.writeInt(-3);
            outStream.flush();
            buffer.put(byteOut.toByteArray());
            buffer.flip();
            channel.write(buffer);
            byteOut.reset();
            
            System.out.println("\033[94mContenido enviado:\033[0m");
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-3));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             CREAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void crearDirectorio(DatagramChannel channel) {
        try {
            System.out.println("\n\033[95m -- CREAR DIRECTORIO --\033[0m");

            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            String path = JOptionPane.showInputDialog("Nombre del nuevo directorio");
            if(path == null)
                return;

            // Enviar datos
            byte[] path_bytes = path.getBytes();
            outStream.writeInt(-4);
            outStream.writeInt(path_bytes.length);
            outStream.write(path_bytes);
            outStream.flush();
            buffer.put(byteOut.toByteArray());
            buffer.flip();
            channel.write(buffer);
            byteOut.reset();
            
            System.out.println("\033[94mContenido enviado:\033[0m");
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-4));
            System.out.println("\033[93mLongitud del directorio: \033[0m"+path_bytes.length);
            System.out.println("\033[93mNombre del directorio: \033[0m"+new String(path_bytes));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                            ELIMINAR ARCHIVO
    // ------------------------------------------------------------------------
    static void eliminarArchivo(DatagramChannel channel) {
        try {
            System.out.println("\n\033[95m -- ELIMINAR ARCHIVO / DIRECTORIO --\033[0m");
            
            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            String path = JOptionPane.showInputDialog("Nombre del archivo/directorio");
            if(path == null)
                return;

            // Enviar datos
            byte[] path_bytes = path.getBytes();
            outStream.writeInt(-5);
            outStream.writeInt(path_bytes.length);
            outStream.write(path_bytes);
            outStream.flush();
            buffer.put(byteOut.toByteArray());
            buffer.flip();
            channel.write(buffer);
            byteOut.reset();
            
            System.out.println("\033[94mContenido enviado:\033[0m");
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-5));
            System.out.println("\033[93mLongitud del nombre: \033[0m"+path_bytes.length);
            System.out.println("\033[93mNombre del archivo: \033[0m"+new String(path_bytes));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             RENOMBRAR ARCHIVO
    // ------------------------------------------------------------------------
    static void renombrarArchivo(DatagramChannel channel) {
        try {
            System.out.println("\n\033[95m -- RENOMBRAR ARCHIVO / DIRECTORIO --\033[0m");

            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            String original = JOptionPane.showInputDialog("Nombre del archivo original");
            if(original == null)
                return;
            String nuevo = JOptionPane.showInputDialog("Nuevo nombre");
            if(nuevo == null)
                return;
            
            // Enviar datos
            byte[] original_bytes = original.getBytes();
            byte[] nuevo_bytes = nuevo.getBytes();
            outStream.writeInt(-7);
            outStream.writeInt(original_bytes.length);
            outStream.write(original_bytes);
            outStream.writeInt(nuevo_bytes.length);
            outStream.write(nuevo_bytes);
            outStream.flush();
            buffer.put(byteOut.toByteArray());
            buffer.flip();
            channel.write(buffer);
            byteOut.reset();

            System.out.println("\033[94mContenido enviado:\033[0m");
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-7));
            System.out.println("\033[93mLongitud del nombre original: \033[0m"+original_bytes.length);
            System.out.println("\033[93mNombre original: \033[0m"+new String(original_bytes));
            System.out.println("\033[93mLongitud del nuevo nombre: \033[0m"+nuevo_bytes.length);
            System.out.println("\033[93mNuevo nombre: \033[0m"+new String(nuevo_bytes));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                              SUBIR ARCHIVO
    // ------------------------------------------------------------------------
    static void subirArchivo(DatagramChannel channel) {
        String rutaArchivo;
        String nombreArchivo;
        try {
            SocketAddress serverAddress = new InetSocketAddress(InetAddress.getByName(dir_host), PORT);
            // Clases para enviar información
            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            // Seleccionar archivo
            JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            int returnValue = jfc.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = jfc.getSelectedFile();
                rutaArchivo = selectedFile.getAbsolutePath();
                nombreArchivo = selectedFile.getName();
            } else
                return;
            
            Path path = Paths.get(rutaArchivo);
            byte[] file = Files.readAllBytes(path);
            byte[] fileNameBytes = nombreArchivo.getBytes();

            int tam = TAM_PAQUETE;
            int PAQUETES_COMPLETOS = file.length / TAM_PAQUETE;
            int TOTAL_PAQUETES = (file.length % TAM_PAQUETE == 0) ? PAQUETES_COMPLETOS : PAQUETES_COMPLETOS + 1;
            int n_sobrantes = file.length % TAM_PAQUETE;

            int start = 0; // Apuntador al inicio de la ventana
            int apuntador = 0; // Apuntador al paquete que se va a mandar

            while (start < TOTAL_PAQUETES) {
            // Enviar paquetes en la ventana
            while (apuntador < start + TAM_VENTANA && apuntador < TOTAL_PAQUETES) {
                byte[] btmp;
                if (apuntador == PAQUETES_COMPLETOS)
                    btmp = Arrays.copyOfRange(file, apuntador * tam, apuntador * tam + n_sobrantes);
                else
                    btmp = Arrays.copyOfRange(file, apuntador * tam, apuntador * tam + tam);

                outStream.writeInt(apuntador);              // Número de paquete
                outStream.writeInt(TOTAL_PAQUETES);         // Total de paquetes
                outStream.writeInt(fileNameBytes.length);   // Tamaño del nombre del archivo
                outStream.write(fileNameBytes);             // Nombre del archivo
                outStream.writeInt(btmp.length);            // Tamaño de los datos
                outStream.write(btmp);                      // Datos
                outStream.flush();

                byte[] bufferOut = byteOut.toByteArray();
                buffer.clear();
                buffer.put(bufferOut);
                buffer.flip();
                channel.send(buffer, serverAddress); // Enviar paquete
                byteOut.reset();
                apuntador++;
            }

            try {
                // Recibir el ACK
                buffer.clear();
                channel.configureBlocking(false);
                long startTime = System.currentTimeMillis();
                boolean ackReceived = false;
                while (!ackReceived && (System.currentTimeMillis() - startTime) < TIEMPO_ESPERA_ENVIAR) {
                    buffer.clear();
                    SocketAddress sender = channel.receive(buffer);
                    if (sender != null) {
                        buffer.flip();
                        byteIn = new ByteArrayInputStream(buffer.array(), 0, buffer.limit());
                        inStream = new DataInputStream(byteIn);
                        int n = inStream.readInt(); // Leer número de paquete ACK
                        System.out.println("\033[95mACK: \033[0m" + n);
                        System.out.flush();
                        if (n >= start) {
                            start = n + 1; // Mover el inicio de la ventana
                            ackReceived = true;
                        }
                    }
                }

                if (!ackReceived) {
                    throw new SocketTimeoutException();
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\033[31mTIMEOUT: retransmitiendo desde el paquete " + start + "\033[0m");
                System.out.flush();
                apuntador = start; // Empezar a retransmitir los paquetes desde el inicio de la ventana
            }
        }
        } catch (Exception e) {
            
        }
    }
}
