import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import javax.swing.JOptionPane;

public class ClienteNoBloqueante {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;

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
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+-4);
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
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+-4);
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
            System.out.println("\033[93mCódigo de instrucción: \033[0m"+(-4));
            System.out.println("\033[93mLongitud del nombre original: \033[0m"+original_bytes.length);
            System.out.println("\033[93mNombre original: \033[0m"+new String(original_bytes));
            System.out.println("\033[93mLongitud del nuevo nombre: \033[0m"+nuevo_bytes.length);
            System.out.println("\033[93mNuevo nombre: \033[0m"+new String(nuevo_bytes));

            estado = Estado.RECIBIENDO_DIRECTORIO;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
