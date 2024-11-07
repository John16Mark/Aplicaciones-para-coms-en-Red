import java.net.*;
import java.io.*;
/**
 *
 * @author axele
 */
public class SMetadatos {
    public static void main(String[] args){
        try{
          DatagramSocket s = new DatagramSocket(5555);
            System.out.println("Servidor esperando datagrama..");
            for(;;){
              DatagramPacket p = new DatagramPacket(new byte[65535],65535);    
              s.receive(p);
              DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
              int n = dis.readInt();          // Número de paquete
              int tam = dis.readInt();        // Tamaño del paquete
              byte[] b = new byte[tam];       // datos en bytes
              int x = dis.read(b);
              String cadena = new String(b);  // Cadena de los bytes
              System.out.println("Paquete recibido con los datos: #paquete->"+ n+ " con "+tam+" bytes y el mensaje:"+cadena);
              System.out.println("x: "+x);
              dis.close();
            }//for
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
