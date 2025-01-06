import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.file.Path;

public class ClienteNoBloqueante {
    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    //final static String fileName = "./archivo.txt";
    static String nombreArchivo = "";
    static String rutaArchivo = "";
    static Path rutaDirectorio;
    static Thread hiloConexion;

    static Window ventana;

    public static void main(String[] args) throws Exception{
        // Configurar canal del cliente en modo no bloqueante
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(InetAddress.getByName(dir_host), PORT));

        // Enviar SYN
        ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream outStream = new DataOutputStream(byteOut);

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

        // Recibir SYN-ACK
        buffer.clear();
        channel.read(buffer);
        buffer.flip();
        DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(buffer.array(), 0, buffer.limit()));

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

    }
}
