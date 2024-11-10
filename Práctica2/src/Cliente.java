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
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA = 2000;

    final static String ruta = "./archivo.txt";

    public static void main(String[] args){
        try{
            Path path = Paths.get(ruta);
            byte[] bytes = Files.readAllBytes(path);

            int tam = TAM_VENTANA;
            int MAX_PAQUETES = (int) bytes.length/TAM_VENTANA;
            int TOTAL_PAQUETES = (int) bytes.length%TAM_VENTANA == 0 ? MAX_PAQUETES : MAX_PAQUETES+1;

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramSocket socket = new DatagramSocket();
            InetAddress direccion = InetAddress.getByName(dir_host);

            // ---------------------------------
            //             HANDSHAKE
            // ---------------------------------
            
            // Primer mensaje
            String SYN = "SYN";
            byte[] SYNBytes = SYN.getBytes();
            outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
            outStream.write(SYNBytes);              // SYN
            outStream.flush();
            System.out.println("\033[93mEnviando SYN\033[0m");
            byte[] bufferSYN = byteOut.toByteArray();
            DatagramPacket packetSYN = new DatagramPacket(bufferSYN, bufferSYN.length, direccion, PORT);
            socket.send(packetSYN);
            byteOut.reset();
            // Recibir mensaje

/*
            
            outStream.writeInt(TOTAL_PAQUETES); // Total de paquetes
            byte[] temp = ruta.getBytes();
            outStream.writeInt(temp.length);    // Tamaño de la ruta
            outStream.write(temp);              // Ruta
            outStream.flush();
            // Enviar paquete
            System.out.println("enviando SYN");
            byte[] bufferOut = byteOut.toByteArray();
            DatagramPacket packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
            socket.send(packet);
            byteOut.reset();*/

            int currentPacket = 0;
            while(currentPacket < MAX_PAQUETES) {
                outStream.writeInt(currentPacket);  // Número de paquete
                byte[] btmp = Arrays.copyOfRange(bytes, currentPacket*tam, currentPacket*tam + tam);
                outStream.writeInt(btmp.length);    // Tamaño de paquete
                outStream.write(btmp);              // Datos
                outStream.flush();

                // Enviar paquete
                System.out.println("Enviando el paquete "+currentPacket+" con el mensaje: "+new String(btmp));
                byte[] bufferOut = byteOut.toByteArray();
                DatagramPacket packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
                socket.send(packet);
                byteOut.reset();

                // Recibir el ACK
                byte[] buffer = new byte[TAM_BUFFER];
                DatagramPacket ACK = new DatagramPacket(buffer, buffer.length);
                socket.receive(ACK);
                DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(packet.getData()));
                int n = inStream.readInt();
                System.out.println("\033[96mACK: \033[0m"+n);
                if(n == currentPacket)
                    currentPacket++;
            }

            if((int) bytes.length % TAM_VENTANA != 0) {
                while(currentPacket == MAX_PAQUETES) {
                    int n_sobrantes = (int) bytes.length % TAM_VENTANA;
                    outStream.writeInt(MAX_PAQUETES);   // Número de paquete
                    byte[] btmp = Arrays.copyOfRange(bytes, (MAX_PAQUETES)*tam, (MAX_PAQUETES)*tam + n_sobrantes);
                    outStream.writeInt(btmp.length);    // Tamaño de paquete
                    outStream.write(btmp);              // Datos
                    outStream.flush();

                    // Enviar paquete
                    System.out.println("Enviando el paquete "+(MAX_PAQUETES)+" con el mensaje: "+new String(btmp));
                    byte[] bufferOut = byteOut.toByteArray();
                    DatagramPacket packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
                    socket.send(packet);
                    byteOut.reset();

                    // Recibir el ACK
                    byte[] buffer = new byte[TAM_BUFFER];
                    DatagramPacket ACK = new DatagramPacket(buffer, buffer.length);
                    socket.receive(ACK);
                    DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(packet.getData()));
                    int n = inStream.readInt();
                    System.out.println("\033[96mACK: \033[0m"+n);
                    if(n == currentPacket)
                        currentPacket++;
                }
            }

            outStream.close();
            socket.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
