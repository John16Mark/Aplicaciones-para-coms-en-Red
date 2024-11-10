import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.DatagramPacket;
import java.util.Arrays;

public class Cliente {

    final public static int TAM_VENTANA = 10;
    final public static int TAM_PAQUETE = 20;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA = 2000;

    final static String ruta = "./archivo.txt";

    public static void main(String[] args){
        try{
            Path path = Paths.get(ruta);
            byte[] bytes = Files.readAllBytes(path);

            int tam = TAM_PAQUETE;
            int MAX_PAQUETES = (int) bytes.length/TAM_PAQUETE;
            int TOTAL_PAQUETES = (int) bytes.length%TAM_PAQUETE == 0 ? MAX_PAQUETES : MAX_PAQUETES+1;

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

            // ------------------------------------------------------------------------
            //                                   DATOS
            // ------------------------------------------------------------------------
            
            int currentPacket = 0;
            int n_sobrantes = (int) bytes.length % TAM_PAQUETE;
            while(currentPacket < TOTAL_PAQUETES) {
                outStream.writeInt(currentPacket);  // Número de paquete
                byte[] btmp;
                // Si el paquete final es menor a la 
                if(currentPacket == MAX_PAQUETES)
                    btmp = Arrays.copyOfRange(bytes, currentPacket*tam, currentPacket*tam + n_sobrantes);
                else
                    btmp = Arrays.copyOfRange(bytes, currentPacket*tam, currentPacket*tam + tam);
                outStream.writeInt(btmp.length);    // Tamaño de paquete
                outStream.write(btmp);              // Datos
                outStream.flush();

                // Enviar paquete
                System.out.println("Enviando el paquete "+currentPacket+" con el mensaje: "+new String(btmp));
                byte[] bufferOut = byteOut.toByteArray();
                packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
                socket.send(packet);
                byteOut.reset();

                // Recibir el ACK
                buffer = new byte[TAM_BUFFER];
                packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                byteIn = new ByteArrayInputStream(packet.getData());
                inStream = new DataInputStream(byteIn);
                int n = inStream.readInt();
                System.out.println("\033[96mACK: \033[0m"+n);
                if(n == currentPacket)
                    currentPacket++;
            }

            outStream.close();
            socket.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
