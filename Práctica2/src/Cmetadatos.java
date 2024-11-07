import java.net.*;
import java.io.*;
import java.util.Arrays;

/**
 *
 * @author axele
 */
public class Cmetadatos {
    public static void main(String[] args){
        try{
            String mensaje="este es un mensaje pequeño, aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.";
            byte[] tmp = mensaje.getBytes();
            int tam = (int)tmp.length/2;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            DatagramSocket cl = new DatagramSocket();
            for(int ii=0;ii<2;ii++){
                dos.writeInt(ii);
                byte[] btmp = Arrays.copyOfRange(tmp, ii * tam, ((ii * tam) + (tam)));
                System.out.println("Enviando el paquete "+ii+" con el mensaje: "+new String(btmp));
                dos.writeInt(btmp.length);
                dos.write(btmp);
                dos.flush();
                byte[] b = baos.toByteArray();
                //cl.setBroadcast(true);
                InetAddress dir = InetAddress.getByName("127.0.0.1");
                //InetAddress dir = InetAddress.getByName("192.168.10.199");
                DatagramPacket p = new DatagramPacket(b,b.length,dir,5555);
                cl.send(p);
                System.out.println("mensaje enviado..");
                baos.reset();
            }//for
            dos.close();
            cl.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
}
