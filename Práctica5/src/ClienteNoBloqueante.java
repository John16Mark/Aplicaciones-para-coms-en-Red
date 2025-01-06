import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

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

                    estado = Estado.RECIBIENDO_DIRECTORIO;
                    break;

                case RECIBIENDO_DIRECTORIO:
                    // Recibir contenido del directorio
                    inStream = new DataInputStream(new ByteArrayInputStream(buffer.array(), 0, buffer.limit()));
                    if (buffer.limit() == 0) continue; // Esperar más datos

                    int dirTAM = inStream.readInt();
                    byte[] bufferdir = new byte[dirTAM];
                    inStream.read(bufferdir);
                    String directorio = new String(bufferdir);
                    System.out.println("\033[93mRecibido directorio: " + directorio + "\033[0m");

                    int contenidoTAM = inStream.readInt();
                    byte[] bufferContenido = new byte[contenidoTAM];
                    inStream.read(bufferContenido);
                    String contenido = new String(bufferContenido);
                    System.out.println("\033[93mRecibido contenido: " + contenido + "\033[0m");

                    estado = Estado.CONEXION_ESTABLECIDA;
                    break;

                case CONEXION_ESTABLECIDA:
                    // Aquí manejarás el envío/recepción de otros datos
                    System.out.println("\033[92mConexión establecida. Listo para enviar/recibir más datos.\033[0m");
                    return;

                default:
                    throw new IllegalStateException("\033[31mEstado desconocido\033[0m");
            }
        }
    }
}
