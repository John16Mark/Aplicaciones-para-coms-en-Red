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

            while(true) {
                // Recibir paquete
                byte[] buffer = new byte[TAM_BUFFER];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Flujo de entrada
                DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(packet.getData()));
                int n = inStream.readInt();         // Número de paquete
                int tam = inStream.readInt();       // Tamaño del paquete
                byte[] b = new byte[tam];           // datos en bytes
                int x = inStream.read(b);
                String cadena = new String(b);      // Cadena de los bytes
                
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
                Files.write(path, b);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}