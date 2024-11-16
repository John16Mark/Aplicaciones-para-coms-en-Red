import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.List;
import java.util.stream.Collectors;

public class Servidor {

    final public static int TAM_VENTANA = 10;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA = 2000;

    static String dir_server;
    static Path dir_actual;

    public static void main(String[] args) {
        // Crear el socket para recibir
        
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(TIEMPO_ESPERA);
            System.out.println("\033[94mServidor abierto\nEsperando datagrama...\033[0m");
            boolean handshake = false;
            int expectedPacket = 0;

            String currentPath = new java.io.File(".").getCanonicalPath();
            dir_server = currentPath+"\\server\\";
            System.out.println("Directorio:\n" + dir_server);
            dir_actual = Paths.get(dir_server);

            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            // Información del archivo
            int totalPackets = -1;
            String nombreArchivo = "";

            while(true) {
                try {
                    if(!handshake) {
                        System.out.println("\033[92mEsperando Handshake.\033[0m");
                        System.out.flush();
                    }
                    // Recibir paquete
                    byte[] buffer = new byte[TAM_BUFFER];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byteIn = new ByteArrayInputStream(packet.getData());
                    inStream = new DataInputStream(byteIn);

                    // Si no se ha establecido conexión.
                    if(!handshake) {

                        // Recibir SYN
                        int SYNTAM = inStream.readInt();
                        byte[] bufferSYN = new byte[SYNTAM];
                        inStream.read(bufferSYN);
                        String SYN = new String(bufferSYN);
                        if(!SYN.equals("SYN")) {
                            throw(new SocketTimeoutException());
                        }
                        System.out.println("\033[93mRecibido "+SYN+"\033[0m");
                        System.out.flush();
                        
                        // Enviar SYN - ACK
                        SYN += " - ACK";
                        byte[] SYNBytes = SYN.getBytes();
                        outStream.writeInt(SYNBytes.length);    // Tamaño cadena SYN
                        outStream.write(SYNBytes);              // SYN
                        outStream.flush();
                        
                        bufferSYN = byteOut.toByteArray();
                        packet = new DatagramPacket(bufferSYN, bufferSYN.length, packet.getAddress(), packet.getPort());
                        System.out.println("\033[93mEnviando "+SYN+"\033[0m");
                        System.out.flush();
                        socket.send(packet);
                        byteOut.reset();
                        
                        // Recbir ACK
                        buffer = new byte[TAM_BUFFER];
                        packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        byteIn = new ByteArrayInputStream(packet.getData());
                        inStream = new DataInputStream(byteIn);

                        SYNTAM = inStream.readInt();
                        bufferSYN = new byte[SYNTAM];
                        inStream.read(bufferSYN);
                        SYN = new String(bufferSYN);
                        System.out.println("\033[93mRecibido "+SYN+"\033[0m");
                        System.out.flush();

                        handshake = true;
                        dir(dir_actual, packet.getAddress(), packet.getPort());
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                                   STANDBY
                    // ------------------------------------------------------------------------
                    int accion = inStream.readInt();
                    if(accion == -1) {
                        System.out.println("\033[96mRecibido paquete Standby.\033[0m");
                        continue;
                    }
                    // ------------------------------------------------------------------------
                    //                            AVANZAR DIRECTORIO
                    // ------------------------------------------------------------------------
                    else if(accion == -2) {
                        System.out.println("\033[92mRecibido código para avanzar directorio\033[0m");
                        byte[] buffer_path = new byte[TAM_BUFFER];
                        inStream.read(buffer_path);
                        String path = new String(buffer_path).trim();
                        System.out.println(path);

                        // Crear el nuevo Path concatenando el directorio actual con el recibido
                        Path nuevoDir = dir_actual.resolve(path); // Resuelve y normaliza el path resultante

                        // Verificar si es un directorio existente
                        if (Files.isDirectory(nuevoDir)) {
                            dir_actual = nuevoDir;
                            System.out.println("\033[94mCambio de directorio exitoso a: \n" + dir_actual + "\033[0m");
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        } else {
                            System.out.println("\033[91mEl directorio no existe: " + nuevoDir+ "\033[0m");
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        }
                        continue;
                    }
                    // ------------------------------------------------------------------------
                    //                             REGRESAR DIRECTORIO
                    // ------------------------------------------------------------------------
                    else if(accion == -3) {
                        System.out.println("\033[92mRecibido código para regresar directorio\033[0m");
                        
                        // Convertir `dir_server` a un Path
                        Path basePath = Paths.get(dir_server).normalize();
                        // Comprobar si `dir_actual` es un subdirectorio de `dir_server`
                        if (dir_actual.startsWith(basePath) && !dir_actual.equals(basePath)) {
                            dir_actual = dir_actual.getParent();
                            System.out.println("\033[94mRetrocediendo un directorio:\n" + dir_actual + "\033[0m");
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        } else {
                            System.out.println("\033[91mNo se puede retroceder. Ya estás en el directorio base:\n" + basePath + "\033[0m");
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        }
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                                  
                    // ------------------------------------------------------------------------
                    int n = accion;                             // Número de paquete
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
                    bufferIn = new byte[tam];                   // datos en bytes
                    inStream.read(bufferIn);
                    //String cadena = new String(bufferIn);       // Cadena de los bytes
                    
                    Path path = Paths.get(dir_server+nombreArchivo);
                    if (expectedPacket == 0) {
                        Files.write(path, new byte[0]);
                    }

                    // Abrir el archivo, escribir los datos y cerrarlo inmediatamente
                    try {
                        Files.write(path, bufferIn, StandardOpenOption.APPEND);
                        // Está en el try para que no mande el acuse si es que no se escribió bien el archivo
                        if(expectedPacket == n) {
                            outStream.writeInt(n);
                            outStream.flush();
                            byte[] bufferOut = byteOut.toByteArray();
                            DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                            socket.send(ACK);
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
                        expectedPacket = 0;
                        //handshake = false;
                        totalPackets = -1;
                        nombreArchivo = "";

                    }
                }
                catch (SocketTimeoutException e) {
                    System.out.println("\033[31mTIMEOUT: no se recibió el paquete esperado.\033[0m");
                    System.out.flush();
                    expectedPacket = 0;
                    handshake = false;
                    totalPackets = -1;
                    nombreArchivo = "";
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
                    
    void enviarInfoDirectorio() {

    }
                    
    static void dir(Path p, InetAddress direccion, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            Path basePath = Paths.get(dir_server).normalize();
            Path hiddenPath = p.normalize();
            if (hiddenPath.startsWith(basePath)) {
                hiddenPath = basePath.relativize(hiddenPath); // Obtener el path relativo
            } else {
                System.out.println("El directorio no es un subdirectorio de dir_server");
                return;
            }

            byte[] path_bytes = hiddenPath.toString().getBytes();
            outStream.writeInt(path_bytes.length);
            outStream.write(path_bytes);
        
            // Obtener lista de archivos y directorios
            List<String> filesAndDirs = Files.list(p)
                                             .map(Path::getFileName)
                                             .map(Path::toString)
                                             .collect(Collectors.toList());

            // Convertir la lista en una cadena separada por comas
            String message = String.join("?", filesAndDirs);

            byte[] buffer_contenido = message.getBytes();
            outStream.writeInt(buffer_contenido.length);
            outStream.write(buffer_contenido);
            byte[] buffer = byteOut.toByteArray();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, direccion, port);
            socket.send(packet);

            System.out.println("Contenido del directorio enviado.");
        } catch (IOException e) {
            System.out.println("Error al enviar el contenido del directorio: " + e.getMessage());
        }
    }
}