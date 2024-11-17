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

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
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
    static Path rutaDirectorio;
    static Thread hiloConexion;

    static Window ventana;

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
            if(!SYN.equals("SYN - ACK")) {
                outStream.close();
                socket.close();
                throw(new Exception("\033[31mError al establecer conexión."));
            }
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

            // Recibir contenido del directorio
            buffer = new byte[TAM_BUFFER];
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            byteIn = new ByteArrayInputStream(packet.getData());
            inStream = new DataInputStream(byteIn);

            int tam_camino = inStream.readInt();
            buffer = new byte[tam_camino];
            inStream.read(buffer);
            String camino = new String(buffer);
            int tam_contenido = inStream.readInt();
            buffer = new byte[tam_contenido];
            inStream.read(buffer);
            String contenido = new String(buffer);

            // Crear el hilo para mantener viva la conexión
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();

            rutaDirectorio = Paths.get("./");
            ventana = new Window(socket, direccion, rutaDirectorio);
            ventana.actualizarDirectorio(camino, contenido);
            //subirArchivo(socket, direccion);

            ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    // Cerrar el outputStream y el socket cuando se cierra la ventana
                    if (outStream != null) {
                        outStream.close();
                    }
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            });
            //outStream.close();
            //socket.close();
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
            hiloConexion.interrupt();

            Path path = Paths.get(rutaArchivo);
            byte[] file = Files.readAllBytes(path);
            byte[] fileNameBytes = nombreArchivo.getBytes();

            int tam = TAM_PAQUETE;
            int PAQUETES_COMPLETOS = (int) file.length/TAM_PAQUETE;
            int TOTAL_PAQUETES = (int) file.length%TAM_PAQUETE == 0 ? PAQUETES_COMPLETOS : PAQUETES_COMPLETOS+1;
            int n_sobrantes = (int) file.length % TAM_PAQUETE;

            int start = 0;      // Apuntador al inicio de la ventana
            int apuntador = 0;  // Apuntador al paquete que se va a mandar
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
                    System.out.flush();
                    apuntador = start; // Empezar a transmitir los paquetes desde el inicio de la ventana
                }
            }
            System.out.println("\033[94mEnvío exitoso del archivo "+nombreArchivo+".\033[0m");
            System.out.flush();

            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();

            

        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    static void borrarArchivo(DatagramSocket socket, InetAddress direccion) {
        try {/*
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramPacket packet;
*/
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                            AVANZAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void avanzarDirectorio(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramPacket packet;

            String path = JOptionPane.showInputDialog("Introduzca el nombre del directorio");
            hiloConexion.interrupt();

            // Enviar paquete con datos para avanzar directorio
            outStream.writeInt(-2);
            byte[] buffer_path = path.getBytes();
            outStream.write(buffer_path);
            byte[] data = byteOut.toByteArray();
            packet = new DatagramPacket(data, data.length, direccion, PORT);
            socket.send(packet);
            System.out.println("\033[92mEnviando avanzar a directorio \033[0m\n"+path);
            System.out.flush();

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             REGRESAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void regresarDirectorio(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramPacket packet;

            hiloConexion.interrupt();

            // Enviar paquete con datos para regresar directorio
            outStream.writeInt(-3);
            byte[] data = byteOut.toByteArray();
            packet = new DatagramPacket(data, data.length, direccion, PORT);
            socket.send(packet);
            System.out.println("\033[92mEnviando regresar directorio \033[0m");
            System.out.flush();

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();
        
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             CREAR DIRECTORIO
    // ------------------------------------------------------------------------
    static void crearDirectorio(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramPacket packet;

            String path = JOptionPane.showInputDialog("Nombre del nuevo directorio");
            hiloConexion.interrupt();

            // Enviar paquete con datos para crear directorio
            outStream.writeInt(-4);
            byte[] buffer_path = path.getBytes();
            outStream.write(buffer_path);
            byte[] data = byteOut.toByteArray();
            packet = new DatagramPacket(data, data.length, direccion, PORT);
            socket.send(packet);
            System.out.println("\033[92mEnviando crear directorio \033[0m\n" + path);
            System.out.flush();

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                            ELIMINAR ARCHIVO
    // ------------------------------------------------------------------------
    static void eliminarArchivo(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramPacket packet;

            String path = JOptionPane.showInputDialog("Nombre del archivo/directorio");
            hiloConexion.interrupt();

            // Enviar paquete con datos para eliminar archivo
            outStream.writeInt(-5);
            byte[] buffer_path = path.getBytes();
            outStream.write(buffer_path);
            byte[] data = byteOut.toByteArray();
            packet = new DatagramPacket(data, data.length, direccion, PORT);
            socket.send(packet);
            System.out.println("\033[92mEnviando eliminar archivo \033[0m\n" + path);
            System.out.flush();

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA));
            hiloConexion.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    static void actualizarDirectorio(DatagramSocket socket) {
        try {
            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            byte[] buffer = new byte[TAM_BUFFER];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            byteIn = new ByteArrayInputStream(packet.getData());
            inStream = new DataInputStream(byteIn);

            int tam_camino = inStream.readInt();
            buffer = new byte[tam_camino];
            inStream.read(buffer);
            String camino = new String(buffer);
            int tam_contenido = inStream.readInt();
            buffer = new byte[tam_contenido];
            inStream.read(buffer);
            String contenido = new String(buffer);

            ventana.actualizarDirectorio(camino, contenido);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

