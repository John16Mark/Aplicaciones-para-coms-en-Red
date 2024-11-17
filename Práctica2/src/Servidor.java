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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Servidor {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    static String dir_server;
    static Path dir_actual;

    public static void main(String[] args) {
        // Crear el socket para recibir
        
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(TIEMPO_ESPERA_RECIBIR);
            System.out.println("\033[94mServidor abierto\nEsperando datagrama...\033[0m");
            boolean handshake = false;

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
            int expectedPacket = 0;
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
                        System.out.flush();
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                            AVANZAR DIRECTORIO
                    // ------------------------------------------------------------------------
                    else if(accion == -2) {
                        System.out.println("\033[92mRecibido código para avanzar directorio\033[0m");
                        System.out.flush();
                        byte[] buffer_path = new byte[TAM_BUFFER];
                        inStream.read(buffer_path);
                        String path = new String(buffer_path).trim();
                        System.out.println(path);

                        Path nuevoDir = dir_actual.resolve(path);

                        // Verificar si es un directorio existente
                        if (Files.isDirectory(nuevoDir)) {
                            dir_actual = nuevoDir;
                            System.out.println("\033[94mCambio de directorio exitoso a: \n" + dir_actual + "\033[0m");
                            System.out.flush();
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        } else {
                            System.out.println("\033[91mEl directorio no existe: " + nuevoDir+ "\033[0m");
                            System.out.flush();
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        }
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                             REGRESAR DIRECTORIO
                    // ------------------------------------------------------------------------
                    else if(accion == -3) {
                        System.out.println("\033[92mRecibido código para regresar directorio\033[0m");
                        System.out.flush();

                        Path basePath = Paths.get(dir_server).normalize();
                        
                        // Si no estamos ya en el directorio base
                        if (dir_actual.startsWith(basePath) && !dir_actual.equals(basePath)) {
                            dir_actual = dir_actual.getParent();
                            System.out.println("\033[94mRetrocediendo un directorio:\n" + dir_actual + "\033[0m");
                            System.out.flush();
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        } else {
                            System.out.println("\033[91mNo se puede retroceder. Ya estás en el directorio base:\n" + basePath + "\033[0m");
                            System.out.flush();
                            dir(dir_actual, packet.getAddress(), packet.getPort());
                        }
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                             CREAR DIRECTORIO
                    // ------------------------------------------------------------------------
                    else if(accion == -4) {
                        System.out.println("\033[92mRecibido código para crear directorio\033[0m");
                        System.out.flush();

                        byte[] buffer_path = new byte[TAM_BUFFER];
                        inStream.read(buffer_path);
                        String path = new String(buffer_path).trim();
                        System.out.println(path);
                        
                        Path nuevoDir = dir_actual.resolve(path);
                        Files.createDirectories(nuevoDir);

                        dir(dir_actual, packet.getAddress(), packet.getPort());
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                            ELIMINAR ARCHIVO
                    // ------------------------------------------------------------------------
                    else if(accion == -5) {
                        System.out.println("\033[92mRecibido código para eliminar archivo/directorio\033[0m");
                        System.out.flush();

                        byte[] buffer_nombre = new byte[TAM_BUFFER];
                        inStream.read(buffer_nombre);
                        String nombre = new String(buffer_nombre).trim();
                        System.out.println(nombre);
                        if(nombre.equals("") || nombre.equals(".") || nombre.equals("..")) {
                            System.out.println("\033[91mDirectorio inválido.\033[0m");
                            System.out.flush();

                            dir(dir_actual, packet.getAddress(), packet.getPort());
                            continue;
                        }

                        // Crear la ruta del archivo o directorio
                        Path pathToDelete = dir_actual.resolve(nombre);

                        try {
                            if (Files.exists(pathToDelete)) {
                                if (Files.isDirectory(pathToDelete)) {
                                    if (Files.list(pathToDelete).findAny().isPresent()) {
                                        System.out.println("\033[91mEl directorio tiene elementos. No se puede eliminar.\033[0m");
                                        System.out.flush();
                                    } else {
                                        Files.delete(pathToDelete);
                                        System.out.println("\033[94mDirectorio eliminado:\n" + pathToDelete + "\033[0m");
                                        System.out.flush();
                                    }
                                } else {
                                    // Eliminar el archivo
                                    Files.delete(pathToDelete);
                                    System.out.println("\033[94mArchivo eliminado\n" + pathToDelete + "\033[0m");
                                    System.out.flush();
                                }
                            } else {
                                System.out.println("\033[91mEl archivo o directorio no existe:\n" + pathToDelete + "\033[0m");
                                System.out.flush();
                            }
                        } catch (IOException e) {
                            System.err.println("\033[91mError al intentar eliminar el archivo o directorio:\n" + e.getMessage() + "\033[0m");
                            System.out.flush();
                        }

                        dir(dir_actual, packet.getAddress(), packet.getPort());
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                              BAJAR ARCHIVO
                    // ------------------------------------------------------------------------
                    else if(accion == -6) {
                        System.out.println("\033[92mRecibido código para bajar archivo.\033[0m");
                        System.out.flush();

                        byte[] buffer_nombre = new byte[TAM_BUFFER];
                        inStream.read(buffer_nombre);
                        String nombre = new String(buffer_nombre).trim();
                        System.out.println(nombre);

                        Path path = dir_actual.resolve(nombre);
                        System.out.println("camino: "+path);
                        byte[] file = Files.readAllBytes(path);
                        byte[] fileNameBytes = nombre.getBytes();

                        socket.setSoTimeout(TIEMPO_ESPERA_ENVIAR);

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
                                DatagramPacket packet_bajar = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                                socket.send(packet_bajar);
                                byteOut.reset();
                                    apuntador++;
                            }

                            try {
                                // Recibir el ACK
                                socket.setSoTimeout(TIEMPO_ESPERA_ENVIAR);
                                byte[] buffer_ACK = new byte[TAM_BUFFER];
                                DatagramPacket packet_bajar = new DatagramPacket(buffer_ACK, buffer_ACK.length);
                                socket.receive(packet_bajar);
                                byteIn = new ByteArrayInputStream(packet_bajar.getData());
                                inStream = new DataInputStream(byteIn);
                                
                                int n = inStream.readInt();
                                System.out.println("\033[95mACK: \033[0m"+n);
                                System.out.flush();
                                if (n >= start)
                                    start = n + 1; // Mover el inicio de la ventana

                            } catch (SocketTimeoutException e) {
                                System.out.println("\033[31mTIMEOUT: retransmitiendo desde el paquete " + start + "\033[0m");
                                System.out.flush();
                                apuntador = start; // Empezar a transmitir los paquetes desde el inicio de la ventana
                            }
                        }
                        System.out.println("\033[94mEnvío exitoso del archivo "+nombreArchivo+".\033[0m");
                        System.out.flush();

                        socket.setSoTimeout(TIEMPO_ESPERA_RECIBIR);
                        continue;
                    }

                    // ------------------------------------------------------------------------
                    //                              SUBIR ARCHIVO
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
                        System.out.flush();
                        dir(dir_actual, packet.getAddress(), packet.getPort());
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
                System.out.flush();
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
            System.out.flush();
        } catch (IOException e) {
            System.out.println("Error al enviar el contenido del directorio: " + e.getMessage());
            System.out.flush();
        }
    }
}