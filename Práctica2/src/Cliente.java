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
import java.nio.file.StandardOpenOption;
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
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

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
            byte[] SYN_bytes = SYN.getBytes();
            outStream.writeInt(SYN_bytes.length);    // Tamaño cadena SYN
            outStream.write(SYN_bytes);              // SYN
            outStream.flush();
            
            byte[] buffer_SYN = byteOut.toByteArray();
            DatagramPacket packet = new DatagramPacket(buffer_SYN, buffer_SYN.length, direccion, PORT);
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
            buffer_SYN = new byte[SYNTAM];
            inStream.read(buffer_SYN);
            SYN = new String(buffer_SYN);
            if(!SYN.equals("SYN - ACK")) {
                outStream.close();
                socket.close();
                throw(new Exception("\033[31mError al establecer conexión."));
            }
            System.out.println("\033[93mRecibido "+SYN+"\033[0m");

            // Enviar ACK
            SYN = SYN.substring(6, 9);
            SYN_bytes = SYN.getBytes();
            outStream.writeInt(SYN_bytes.length);    // Tamaño cadena SYN
            outStream.write(SYN_bytes);              // SYN
            outStream.flush();
            
            buffer_SYN = byteOut.toByteArray();
            packet = new DatagramPacket(buffer_SYN, buffer_SYN.length, direccion, PORT);
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
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
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
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
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
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
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
            if(path == null)
                return;
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
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
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
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
            hiloConexion.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                              BAJAR ARCHIVO
    // ------------------------------------------------------------------------
    static void bajarArchivo(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramPacket packet;

            String nombre = JOptionPane.showInputDialog("Nombre del archivo");
            if(nombre == null)
                return;
            JFileChooser f = new JFileChooser();
            f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); 
            int returnValue = f.showSaveDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = f.getSelectedFile();
                rutaArchivo = selectedFile.getAbsolutePath();
            } else
                return;

            Path dir_actual = Paths.get(rutaArchivo);
            System.out.println(rutaArchivo);

            hiloConexion.interrupt();

            // Enviar paquete con datos para eliminar archivo
            outStream.writeInt(-6);
            byte[] buffer_path = nombre.getBytes();
            outStream.write(buffer_path);
            byte[] data = byteOut.toByteArray();
            DatagramPacket packet_temp = new DatagramPacket(data, data.length, direccion, PORT);
            socket.send(packet_temp);
            outStream.flush();
            System.out.println("\033[92mEnviando bajar archivo \033[0m\n" + nombre);
            System.out.flush();

            byteOut = new ByteArrayOutputStream();
            outStream = new DataOutputStream(byteOut);

            // Información del archivo
            int totalPackets = -1;
            int expectedPacket = 0;
            String nombreArchivo = "";

            socket.setSoTimeout(TIEMPO_ESPERA_RECIBIR);
            while(true) {
                try {
                    // Recibir paquete
                    byte[] buffer = new byte[TAM_BUFFER];
                    packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byteIn = new ByteArrayInputStream(packet.getData());
                    inStream = new DataInputStream(byteIn);

                    int n = inStream.readInt();
                    if(totalPackets == -1)
                        totalPackets = inStream.readInt();      // Total de paquetes
                    else
                        inStream.readInt();
                    int tamFileName = inStream.readInt();       // Tamaño de la ruta del archivo
                    byte[] bufferIn = new byte[tamFileName];    // Ruta del archivo en bytes
                    inStream.read(bufferIn);
                    if(nombreArchivo == "")
                        nombreArchivo = new String(bufferIn);   // Cadena de los bytes
                    int tam = inStream.readInt();               // Tamaño de los datos
                    bufferIn = new byte[tam];
                    inStream.read(bufferIn);                    // datos en bytes

                    //Path path = Paths.get(dir_server+nombreArchivo);
                    Path path = dir_actual.resolve(nombreArchivo);
                    if (expectedPacket == 0) {
                        Files.write(path, new byte[0]);
                    }

                    // Abrir el archivo, escribir los datos y cerrarlo inmediatamente
                    try {
                        Files.write(path, bufferIn, StandardOpenOption.APPEND);
                        // Está en el try para que no mande el acuse si es que no se escribió bien el archivo
                        if(expectedPacket == n) {
                            outStream.writeInt(n);
                            byte[] bufferOut = byteOut.toByteArray();
                            DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                            socket.send(ACK);
                            outStream.flush();
                            byteOut.reset();
                            expectedPacket++;
                        }
                    } catch (IOException e) {
                        System.err.println("Error al escribir en el archivo: " + e.getMessage());
                        System.err.flush();
                    }

                    System.out.println("\033[92mPaquete recibido. \033[95m#paq: \033[0m"+ n+ "\t\033[95mTotalPaq: \033[0m"+totalPackets+"\t\033[95mFileName: \033[0m"+nombreArchivo+"\t\033[95mtam: \033[0m"+tam+" bytes");
                    System.out.flush();
                    //System.out.println(cadena);
                    inStream.close();

                    if(expectedPacket == totalPackets) {
                        System.out.println("\033[94mRecibo exitoso del archivo "+nombreArchivo+".\033[0m");
                        System.out.flush();
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("\033[31mTIMEOUT: no se recibió el paquete esperado.\033[0m");
                    System.out.flush();
                    expectedPacket = 0;
                    totalPackets = -1;
                    nombreArchivo = "";
                }
            }
            socket.setSoTimeout(0);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
            hiloConexion.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                             RENOMBRAR ARCHIVO
    // ------------------------------------------------------------------------
    static void renombrarArchivo(DatagramSocket socket, InetAddress direccion) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);
            DatagramPacket packet;

            String original = JOptionPane.showInputDialog("Nombre del archivo original");
            if(original == null)
                return;
            String nuevo = JOptionPane.showInputDialog("Nuevo nombre");
            if(nuevo == null)
                return;
            hiloConexion.interrupt();

            // Enviar paquete con datos para crear directorio
            outStream.writeInt(-7);
            byte[] original_bytes = original.getBytes();
            outStream.writeInt(original_bytes.length);
            outStream.write(original_bytes);
            byte[] nuevo_bytes = nuevo.getBytes();
            outStream.writeInt(nuevo_bytes.length);
            outStream.write(nuevo_bytes);

            byte[] buffer = byteOut.toByteArray();
            packet = new DatagramPacket(buffer, buffer.length, direccion, PORT);
            socket.send(packet);
            System.out.println("\033[92mEnviando renombrar archivo \033[0m\n" + original);
            System.out.println(nuevo);
            System.out.flush();

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            // Continuar Standby
            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
            hiloConexion.start();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    //                              SUBIR ARCHIVO
    // ------------------------------------------------------------------------
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
            } else
                return;

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
                    socket.send(packet);
                    byteOut.reset();
                        apuntador++;
                }

                try {
                    // Recibir el ACK
                    socket.setSoTimeout(TIEMPO_ESPERA_ENVIAR);
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

            // Recibir contenido del directorio
            actualizarDirectorio(socket);

            hiloConexion = new Thread(new HiloConexion(socket, direccion, PORT, -1, TIEMPO_ESPERA_ENVIAR));
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

