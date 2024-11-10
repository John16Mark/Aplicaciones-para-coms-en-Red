import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Servidor {

    final public static int TAM_VENTANA = 10;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;

    final static String dir_server = "./server/";
    final static String nombre_archivo = "archivo.txt";

    public static void main(String[] args) {
        // Crear el socket para recibir
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            socket.setReuseAddress(true);
            System.out.println("\033[94mServidor abierto\nEsperando datagrama...\033[0m");
            int expectedPacket = 0;

            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            // Información del archivo
            int totalPackets = -1;
            //String nombreArchivo = "";

            while(true) {
                // Recibir paquete
                byte[] buffer = new byte[TAM_BUFFER];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byteIn = new ByteArrayInputStream(packet.getData());
                inStream = new DataInputStream(byteIn);

                // Si no se ha establecido conexión.
                if(totalPackets == -1) {
                    
                    // Recibir SYN
                    int SYNTAM = inStream.readInt();
                    byte[] bufferSYN = new byte[SYNTAM];
                    inStream.read(bufferSYN);
                    String SYN = new String(bufferSYN);
                    System.out.println("\033[93mRecibido "+SYN+"\033[0m");
                    
                    // Enviar SYN - ACK
                    SYN += " - ACK";
                    byte[] SYNBytes = SYN.getBytes();
                    outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
                    outStream.write(SYNBytes);              // SYN
                    outStream.flush();
                    
                    bufferSYN = byteOut.toByteArray();
                    packet = new DatagramPacket(bufferSYN, bufferSYN.length, packet.getAddress(), packet.getPort());
                    System.out.println("\033[93mEnviando "+SYN+"\033[0m");
                    socket.send(packet);
                    byteOut.reset();
                    
                    // Recbir ACK
                    buffer = new byte[TAM_BUFFER];
                    packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byteIn = new ByteArrayInputStream(packet.getData());
                    inStream = new DataInputStream(byteIn);

                    SYNTAM = inStream.readInt();
                    bufferSYN = new byte[SYNTAM];
                    inStream.read(bufferSYN);
                    SYN = new String(bufferSYN);
                    System.out.println("\033[93mRecibido "+SYN+"\033[0m");

                    totalPackets = 0;
                    continue;
                }

                // Flujo de entrada
                int n = inStream.readInt();                 // Número de paquete
                totalPackets = inStream.readInt();          // Total de paquetes
                int tamFileName = inStream.readInt();       // Tamaño de la ruta del archivo
                byte[] bufferIn = new byte[tamFileName];    // Ruta del archivo en bytes
                inStream.read(bufferIn);
                String fileName = new String(bufferIn);     // Cadena de los bytes
                int tam = inStream.readInt();               // Tamaño de los datos
                bufferIn = new byte[tam];                   // datos en bytes
                inStream.read(bufferIn);
                String cadena = new String(bufferIn);       // Cadena de los bytes
                
                if(expectedPacket == n) {
                    outStream.writeInt(n);
                    outStream.flush();
                    byte[] bufferOut = byteOut.toByteArray();
                    DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                    socket.send(ACK);
                    byteOut.reset();
                    expectedPacket++;
                }
                
                System.out.println("\033[92mPaquete recibido. \033[95m#paq: \033[0m"+ n+ "\t\033[95mTotalPaq: \033[0m"+totalPackets+"\t\033[95mFileName: \033[0m"+fileName+"\t\033[95mtam: \033[0m"+tam+" bytes");
                System.out.println(cadena);
                inStream.close();

                Path path = Paths.get(dir_server+nombre_archivo);
                Files.write(path, bufferIn);

                if(expectedPacket == totalPackets) {
                    expectedPacket = 0;
                    totalPackets = -1;
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}