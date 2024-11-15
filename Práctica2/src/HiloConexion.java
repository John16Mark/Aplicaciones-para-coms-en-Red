import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HiloConexion implements Runnable {
    private final DatagramSocket socket;
    private final InetAddress direccion;
    private final int puerto;
    private final int codigo;
    private final int TIEMPO_ESPERA;

    public HiloConexion(DatagramSocket socket, InetAddress direccion, int puerto, int codigo, int timeout) {
        this.socket = socket;
        this.direccion = direccion;
        this.puerto = puerto;
        this.codigo = codigo;
        this.TIEMPO_ESPERA = timeout;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                DataOutputStream outStream = new DataOutputStream(byteOut);
                outStream.writeInt(codigo);
                byte[] data = byteOut.toByteArray();
                DatagramPacket packet = new DatagramPacket(data, data.length, direccion, puerto);
                socket.send(packet);
                System.out.println("\033[96mEnviando paquete Standby.\033[0m");
                System.out.flush();
                Thread.sleep(TIEMPO_ESPERA);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
