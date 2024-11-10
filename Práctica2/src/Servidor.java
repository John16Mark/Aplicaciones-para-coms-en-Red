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

            // Información del archivo
            int totalPackets = -1;
            String nombreArchivo = "";

            while(true) {
                // Recibir paquete
                byte[] buffer = new byte[TAM_BUFFER];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(packet.getData()));

                if(totalPackets == -1) {
                    // Recibir SYN
                    int SYNTAM = inStream.readInt();
                    byte[] bufferSYN = new byte[SYNTAM];
                    int SYNx = inStream.read(bufferSYN);
                    String SYN = new String(bufferSYN);
                    System.out.println("\033[93mRecibiendo "+SYN+"\033[0m");
                    // Enviar SYN
                    SYN += " - ACK";
                    byte[] SYNBytes = SYN.getBytes();
                    outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
                    outStream.write(SYNBytes);              // SYN
                    outStream.flush();
                    System.out.println("\033[93mEnviando SYN\033[0m");
                    bufferSYN = byteOut.toByteArray();
                    DatagramPacket packetSYN = new DatagramPacket(bufferSYN, bufferSYN.length, packet.getAddress(), packet.getPort());
                    socket.send(packetSYN);
                    byteOut.reset();
                    
                    totalPackets = 0;
                }

                // Flujo de entrada
                int n = inStream.readInt();         // Número de paquete
                int tam = inStream.readInt();       // Tamaño del paquete
                byte[] bufferIn = new byte[tam];           // datos en bytes
                int x = inStream.read(bufferIn);
                String cadena = new String(bufferIn);      // Cadena de los bytes
                
                if(expectedPacket == n) {
                    outStream.writeInt(n);
                    outStream.flush();
                    byte[] bufferOut = byteOut.toByteArray();
                    DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                    socket.send(ACK);
                    byteOut.reset();
                    expectedPacket++;
                }
                
                System.out.println("\033[92mPaquete recibido. \033[95m#paquete: \033[0m"+ n+ "\t\033[95mtam: \033[0m"+tam+" bytes\t\033[95mx: \033[0m"+x);
                System.out.println(cadena);
                inStream.close();

                Path path = Paths.get(dir_server+nombre_archivo);
                Files.write(path, bufferIn);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}