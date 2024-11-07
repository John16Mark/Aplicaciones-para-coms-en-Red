import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Servidor {

    final public static int TAM_VENTANA = 10;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;

    public static void main(String[] args) {
        try {
            // Crear el socket
            DatagramSocket socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);
            System.out.println("\033[94mServidor abierto\nEsperando datagrama...\033[0m");

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
                System.out.println("\033[92mPaquete recibido. \033[95m#paquete: \033[0m"+ n+ "\t\033[95mtam: \033[0m"+tam+" bytes\tx: \033[0m"+x+"\nmensaje:"+cadena);
                inStream.close();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
}