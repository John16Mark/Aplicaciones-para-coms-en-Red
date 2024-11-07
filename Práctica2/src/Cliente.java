import java.io.ByteArrayOutputStream;
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

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramSocket socket = new DatagramSocket();
            InetAddress direccion = InetAddress.getByName(dir_host);

            for(int i=0; i<MAX_PAQUETES; i++){
                outStream.writeInt(i);              // Número de paquete
                byte[] btmp = Arrays.copyOfRange(bytes, i*tam, i*tam + tam);
                outStream.writeInt(btmp.length);    // Tamaño de paquete
                outStream.write(btmp);              // Datos
                outStream.flush();

                // Enviar paquete
                System.out.println("Enviando el paquete "+i+" con el mensaje: "+new String(btmp));
                byte[] b = byteOut.toByteArray();
                DatagramPacket packet = new DatagramPacket(b, b.length, direccion, PORT);
                socket.send(packet);
                System.out.println("mensaje enviado..");
                byteOut.reset();
            }//for

            if((int) bytes.length % TAM_VENTANA != 0) {
                int n_sobrantes = (int) bytes.length % TAM_VENTANA;
                outStream.writeInt(MAX_PAQUETES);   // Número de paquete
                byte[] btmp = Arrays.copyOfRange(bytes, (MAX_PAQUETES)*tam, (MAX_PAQUETES)*tam + n_sobrantes);
                outStream.writeInt(btmp.length);    // Tamaño de paquete
                outStream.write(btmp);              // Datos
                outStream.flush();
                
                // Enviar paquete
                System.out.println("Enviando el paquete "+(MAX_PAQUETES)+" con el mensaje: "+new String(btmp));
                byte[] b = byteOut.toByteArray();
                DatagramPacket packet = new DatagramPacket(b, b.length, direccion, PORT);
                socket.send(packet);
                System.out.println("mensaje enviado..");
                byteOut.reset();
            }

            outStream.close();
            socket.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
