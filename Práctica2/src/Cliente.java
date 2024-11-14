import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileSystemView;

import java.util.Arrays;

public class Cliente {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static String dir_host = "127.0.0.1";
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA = 500;

    //final static String fileName = "./archivo.txt";
    static String nombreArchivo = "";
    static String rutaArchivo = "";

    public static void main(String[] args){
        try{
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

            subirArchivo(socket, direccion);

            outStream.close();
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        
    }

    static void subirArchivo(DatagramSocket socket, InetAddress direccion) {

        try{
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            // Crear el hilo para enviar el "ping" (-5) cada cierto tiempo
            Thread keepAliveThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        while (true) {
                            // Crear el paquete de datagrama con el valor -5
                            byte[] data = new byte[4]; // Un entero ocupa 4 bytes
                            outStream.writeInt(-5);
                            data = byteOut.toByteArray();
                            DatagramPacket packet = new DatagramPacket(data, data.length, direccion, PORT); // Asegúrate de usar el puerto correcto
                            socket.send(packet);
                            System.out.println("\033[96mEnviando paquete standby.");
                            // Espera 2 segundos antes de volver a enviar el paquete (-5)
                            Thread.sleep(500); // Espera de 2 segundos
                        }
                    } catch (InterruptedException e) {
                        // Salida silenciosa al ser interrumpido
                        Thread.currentThread().interrupt(); // Reestablecer el estado de interrupción
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            keepAliveThread.start();

            // ------------------------------------------------------------------------
            //                                   DATOS
            // ------------------------------------------------------------------------

            DatagramPacket packet;

            JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            int returnValue = jfc.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = jfc.getSelectedFile();
                rutaArchivo = selectedFile.getAbsolutePath();
                nombreArchivo = selectedFile.getName();
            }
            keepAliveThread.interrupt();

            Path path = Paths.get(rutaArchivo);
            byte[] file = Files.readAllBytes(path);
            byte[] fileNameBytes = nombreArchivo.getBytes();

            int tam = TAM_PAQUETE;
            int PAQUETES_COMPLETOS = (int) file.length/TAM_PAQUETE;
            int TOTAL_PAQUETES = (int) file.length%TAM_PAQUETE == 0 ? PAQUETES_COMPLETOS : PAQUETES_COMPLETOS+1;
            int n_sobrantes = (int) file.length % TAM_PAQUETE;

            int start = 0; // Apuntador al inicio de la ventana
            int apuntador = 0; // Apuntador al paquete que se va a mandar
            while (start < TOTAL_PAQUETES) {
                // Enviar paquetes en la ventana
                while (apuntador < start + TAM_VENTANA && apuntador < TOTAL_PAQUETES) {
                    byte[] btmp;
                    // Si es el paquete final (y es más pequeño que el tamaño de paquete)
                    if(apuntador == PAQUETES_COMPLETOS)
                        btmp = Arrays.copyOfRange(file, apuntador*tam, apuntador*tam + n_sobrantes);
                    else
                        btmp = Arrays.copyOfRange(file, apuntador*tam, apuntador*tam + tam);
                    outStream.writeInt(apuntador);              // Número de paquete
                    outStream.writeInt(TOTAL_PAQUETES);         // Total de paquetes
                    outStream.writeInt(fileNameBytes.length);   // Tamaño del nombre del archivo
                    outStream.write(fileNameBytes);             // Nombre del archivo
                    outStream.writeInt(btmp.length);            // Tamaño de los datos
                    outStream.write(btmp);                      // Datos
                    outStream.flush();

                    // Enviar paquete
                    byte[] bufferOut = byteOut.toByteArray();
                    packet = new DatagramPacket(bufferOut, bufferOut.length, direccion, PORT);
                    /*System.out.println("Enviando el paquete "+apuntador+" con el mensaje: ");
                    System.out.println(new String(btmp));*/
                    socket.send(packet);
                    byteOut.reset();
                        apuntador++;
                }

                try {
                    // Recibir el ACK
                    socket.setSoTimeout(TIEMPO_ESPERA);
                    byte[] buffer = new byte[TAM_BUFFER];
                    packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byteIn = new ByteArrayInputStream(packet.getData());
                    inStream = new DataInputStream(byteIn);
                    int n = inStream.readInt();
                    System.out.println("\033[95mACK: \033[0m"+n);
                    System.out.flush();
                    if (n >= start)
                        start = n + 1; // Mover el inicio de la ventana

                } catch (SocketTimeoutException e) {
                    System.out.println("\033[31mTIMEOUT: retransmitiendo desde el paquete " + start+"\033[0m");
                    apuntador = start; // Empezar a transmitir los paquetes desde el inicio de la ventana
                }
            }
            System.out.println("\033[94mEnvío exitoso del archivo "+nombreArchivo+".\033[0m");

            outStream.close();
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    static void borrarArchivo() {

    }

    class Window extends JFrame {
        public Window() {

        }
    }

}
