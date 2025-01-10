import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServidorNoBloqueante {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 40000;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA_ENVIAR = 200;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    static String dir_server;
    static Path dir_actual;

    static DatagramChannel serverChannel;

    enum Estado {
        ESPERANDO_SYN,
        ESPERANDO_ACK,
        CONEXION_ESTABLECIDA,
        FIN_RECEPCION,
    }

    public static void main(String[] args) {
        Estado estado = Estado.ESPERANDO_SYN;
        SocketAddress clienteActual = null;

        try {
            // Canal de datagrama no bloqueante
            serverChannel = DatagramChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(PORT));
            System.out.println("\033[94mServidor abierto\nEsperando datagrama...\033[0m");

            // Selector
            Selector selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_READ);
            
            // Directorios
            dir_server = new java.io.File(".").getCanonicalPath() + "\\data\\";
            dir_actual = Paths.get(dir_server);
            System.out.println("Directorio:\n" + dir_server);

            // Clases para enviar información
            //ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            //DataOutputStream outStream = new DataOutputStream(byteOut);

            // Información del archivo
            int totalPackets = -1;
            int expectedPacket = 0;
            String nombreArchivo = "";

            ByteBuffer buffer = ByteBuffer.allocate(TAM_BUFFER);

            while(true) {
                selector.select();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while(keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if(key.isReadable()) {
                        buffer.clear();
                        SocketAddress cliente = serverChannel.receive(buffer);
                        if(cliente != null) {
                            buffer.flip();
                            // Clases para recibir inormación
                            ByteArrayInputStream byteIn = new ByteArrayInputStream(buffer.array(), 0, buffer.limit());
                            DataInputStream inStream = new DataInputStream(byteIn);

                            switch (estado) {
                            case ESPERANDO_SYN:
                                // Recibir SYN
                                int SYNTAM = inStream.readInt();
                                byte[] bufferSYN = new byte[SYNTAM];
                                inStream.read(bufferSYN);
                                String SYN = new String(bufferSYN);
                                if (!SYN.equals("SYN")) {
                                    System.out.println("\033[31mError: Se esperaba 'SYN'\033[0m");
                                    continue;
                                }
                                System.out.println("\033[93mRecibido " + SYN + "\033[0m");

                                // Enviar SYN-ACK
                                SYN += " - ACK";
                                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                DataOutputStream outStream = new DataOutputStream(byteOut);
                                byte[] SYNBytes = SYN.getBytes();
                                outStream.writeInt(SYNBytes.length);
                                outStream.write(SYNBytes);
                                outStream.flush();

                                ByteBuffer sendBuffer = ByteBuffer.wrap(byteOut.toByteArray());
                                serverChannel.send(sendBuffer, cliente);
                                System.out.println("\033[93mEnviando " + SYN + "\033[0m");
                                byteOut.reset();

                                estado = Estado.ESPERANDO_ACK;
                                clienteActual = cliente; // Guardar cliente actual
                                break;
                            case ESPERANDO_ACK:
                                if (!cliente.equals(clienteActual)) {
                                    System.out.println("\033[31mPaquete recibido de cliente desconocido\033[0m");
                                    continue;
                                }
        
                                // Recibir ACK
                                SYNTAM = inStream.readInt();
                                bufferSYN = new byte[SYNTAM];
                                inStream.read(bufferSYN);
                                SYN = new String(bufferSYN);
        
                                if (!SYN.equals("ACK")) {
                                    System.out.println("\033[31mError: Se esperaba 'ACK'\033[0m");
                                    continue;
                                }
                                System.out.println("\033[93mRecibido " + SYN + "\033[0m");
        
                                estado = Estado.CONEXION_ESTABLECIDA;
                                System.out.println("\033[92mConexión establecida con el cliente: " + cliente + "\033[0m");
                                enviarInfoDirectorio(dir_actual, cliente);
                                break;
                            case CONEXION_ESTABLECIDA:
                                if (!cliente.equals(clienteActual)) {
                                    System.out.println("\033[31mPaquete recibido de cliente desconocido\033[0m");
                                    continue;
                                }
                                //System.out.println("\033[94mPaquete recibido en estado de conexión establecida.\033[0m");

                                int instruccion = inStream.readInt();
                                switch (instruccion) {
                                case -2:
                                    System.out.println("\033[92mRecibido código para avanzar directorio\033[0m");
                                    System.out.flush();
                                    avanzarDirectorio(inStream, cliente); 
                                    break;
                                case -3:
                                    System.out.println("\033[92mRecibido código para regresar directorio\033[0m");
                                    System.out.flush();
                                    regresarDirectorio(cliente); 
                                    break;
                                case -4:
                                    System.out.println("\033[92mRecibido código para crear directorio\033[0m");
                                    System.out.flush();
                                    crearDirectorio(inStream, cliente);
                                    break;
                                case -5:
                                    System.out.println("\033[92mRecibido código para eliminar archivo/directorio\033[0m");
                                    System.out.flush();
                                    eliminarArchivo(inStream, cliente);
                                    break;
                                case -6:
                                    System.out.println("\033[92mRecibido código para bajar archivo\033[0m");
                                    System.out.flush();
                                    bajarArchivo(inStream, cliente);
                                    break;
                                case -7:
                                    System.out.println("\033[92mRecibido código para crear renombrar archivo/directorio\033[0m");
                                    System.out.flush();
                                    renombrarArchivo(inStream, cliente);
                                    break;
                                case -9:
                                    System.out.println("\033[92mRecibido código de mandar directorio\033[0m");
                                    System.out.flush();
                                    enviarInfoDirectorio(dir_actual, cliente);
                                case -10:
                                    System.out.println("\033[92mRecibido código de fin de archivo\033[0m");
                                    System.out.flush();
                                    enviarInfoDirectorio(dir_actual, cliente);;
                                    expectedPacket = 0;
                                    totalPackets = -1;
                                    nombreArchivo = "";
                                default:
                                    break;
                                }
                                // Si recibo un código de instrucción mayor o igual a 0
                                // entonces significa que el Servidor va a recibir un archivo
                                if(instruccion >= 0) {
                                    int n = instruccion;
                                    if(totalPackets == -1)
                                        totalPackets = inStream.readInt();
                                    else inStream.readInt();
                                    int tamFileName = inStream.readInt();       // Tamaño de la ruta del archivo
                                    byte[] bufferIn = new byte[tamFileName];    // Ruta del archivo en bytes
                                    inStream.read(bufferIn);
                                    if(nombreArchivo == "")
                                    nombreArchivo = new String(bufferIn);       // Cadena de los bytes
                                    int tam = inStream.readInt();               // Tamaño de los datos
                                    bufferIn = new byte[tam];
                                    inStream.read(bufferIn);                    // datos en bytes

                                    Path path = dir_actual.resolve(nombreArchivo);
                                    if (expectedPacket == 0) {
                                        Files.write(path, new byte[0]);
                                    }

                                    // Abrir el archivo, escribir los datos y cerrarlo inmediatamente
                                    try {
                                        byteOut = new ByteArrayOutputStream();
                                        outStream = new DataOutputStream(byteOut);
                                        // Está en el try para que no mande el acuse si es que no se escribió bien el archivo
                                        if(expectedPacket == n) {
                                            Files.write(path, bufferIn, StandardOpenOption.APPEND);
                                            
                                            outStream.writeInt(n);
                                            outStream.flush();

                                            sendBuffer = ByteBuffer.wrap(byteOut.toByteArray());
                                            serverChannel.send(sendBuffer, cliente);
                                            System.out.println("\033[93mEnviando ACK:" + n + "\033[0m");
                                            byteOut.reset();

                                            expectedPacket++;
                                        // Si el paquete que se espera es mayor al que llega,
                                        // mandar ACK para empezar a recibir paquetes más adelante
                                        } else if(expectedPacket > n) {
                                            outStream.writeInt(expectedPacket-1);
                                            outStream.flush();

                                            sendBuffer = ByteBuffer.wrap(byteOut.toByteArray());
                                            serverChannel.send(sendBuffer, cliente);
                                            System.out.println("\033[93mEnviando ACK:" + (expectedPacket-1) + "\033[0m");
                                            byteOut.reset();
                                        }
                                    } catch (IOException e) {
                                        System.err.println("Error al escribir en el archivo: " + e.getMessage());
                                        System.err.flush();
                                    }

                                    System.out.println("\033[92mPaquete recibido. \033[95m#paq: \033[0m"+ n+ "\t\033[95mTotalPaq: \033[0m"+totalPackets+"\t\033[95mFileName: \033[0m"+nombreArchivo+"\t\033[95mtam: \033[0m"+tam+" bytes");
                                    System.out.flush();
                                    inStream.close();
                                    if(expectedPacket == totalPackets) {
                                        System.out.println("\033[94mRecibo exitoso del archivo "+nombreArchivo+".\033[0m");
                                        System.out.flush();

                                        // Seguiremos mandando acuses del número total de paquetes
                                        // Hasta recibir el código de fin de archivo (-10)
                                        byteOut = new ByteArrayOutputStream();
                                        outStream = new DataOutputStream(byteOut);
                                        outStream.writeInt(totalPackets);
                                        outStream.flush();

                                        sendBuffer = ByteBuffer.wrap(byteOut.toByteArray());
                                        serverChannel.send(sendBuffer, cliente);
                                        System.out.println("\033[93mEnviando ACK:" + totalPackets + "\033[0m");
                                        byteOut.reset();
                                    }
                                }
                                
                                break;
                            default:
                                System.out.println("\033[31mEstado desconocido\033[0m");
                                break;
                            }
                        }
                    }
                }
                
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        
    }

    // ------------------------------------------------------------------------
    //                            AVANZAR DIRECTORIO
    // ------------------------------------------------------------------------
    private static void avanzarDirectorio(DataInputStream inStream, SocketAddress cliente) throws Exception {
        System.out.println("\033[95m -- AVANZAR DIRECTORIO --\033[0m");

        int tam = inStream.readInt();
        byte[] bufferDirectorio = new byte[tam];
        inStream.read(bufferDirectorio);
        String directorio = new String(bufferDirectorio);

        System.out.println("\033[94mContenido recibido:\033[0m");
        System.out.println("\033[93mLongitud del directorio: \033[0m"+tam);
        System.out.println("\033[93mNombre del directorio: \033[0m"+directorio);

        Path nuevoDir = dir_actual.resolve(directorio);

        // Verificar si es un directorio existente
        if (Files.isDirectory(nuevoDir)) {
            dir_actual = nuevoDir;
            System.out.println("\033[92mCambio de directorio exitoso a: \n" + dir_actual + "\033[0m");
        } else {
            System.out.println("\033[91mEl directorio no existe: " + nuevoDir+ "\033[0m");
        }
        System.out.flush();
        enviarInfoDirectorio(dir_actual, cliente);
    }

    // ------------------------------------------------------------------------
    //                             REGRESAR DIRECTORIO
    // ------------------------------------------------------------------------
    private static void regresarDirectorio(SocketAddress cliente) throws Exception {
        System.out.println("\n\033[95m -- REGRESAR DIRECTORIO --\033[0m");

        Path basePath = Paths.get(dir_server).normalize();
                        
        // Si no estamos ya en el directorio base
        if (dir_actual.startsWith(basePath) && !dir_actual.equals(basePath)) {
            dir_actual = dir_actual.getParent();
            System.out.println("\033[92mRetrocediendo un directorio:\n" + dir_actual + "\033[0m");
        } else {
            System.out.println("\033[91mNo se puede retroceder. Ya estás en el directorio base:\n" + basePath + "\033[0m");
        }

        System.out.flush();
        enviarInfoDirectorio(dir_actual, cliente);
    }

    // ------------------------------------------------------------------------
    //                             CREAR DIRECTORIO
    // ------------------------------------------------------------------------
    private static void crearDirectorio(DataInputStream inStream, SocketAddress cliente) throws Exception {
        System.out.println("\033[95m -- CREAR DIRECTORIO --\033[0m");

        int tam = inStream.readInt();
        byte[] bufferDirectorio = new byte[tam];
        inStream.read(bufferDirectorio);
        String directorio = new String(bufferDirectorio);

        System.out.println("\033[94mContenido recibido:\033[0m");
        System.out.println("\033[93mLongitud del directorio: \033[0m"+tam);
        System.out.println("\033[93mNombre del directorio: \033[0m"+directorio);

        Path nuevoDir = dir_actual.resolve(directorio);
        Files.createDirectories(nuevoDir);
        System.out.println("\033[92mDirectorio creado con éxito.\033[0m");

        enviarInfoDirectorio(dir_actual, cliente);
    }

    // ------------------------------------------------------------------------
    //                            ELIMINAR ARCHIVO
    // ------------------------------------------------------------------------
    private static void eliminarArchivo(DataInputStream inStream, SocketAddress cliente) throws Exception {
        System.out.println("\033[95m -- ELIMINAR ARCHIVO / DIRECTORIO --\033[0m");

        int tam = inStream.readInt();
        byte[] bufferNombre = new byte[tam];
        inStream.read(bufferNombre);
        String nombre = new String(bufferNombre);

        System.out.println("\033[94mContenido recibido:\033[0m");
        System.out.println("\033[93mLongitud del nombre: \033[0m"+tam);
        System.out.println("\033[93mNombre del archivo: \033[0m"+nombre);

        if(nombre.equals("") || nombre.equals(".") || nombre.equals("..")) {
            System.out.println("\033[91mDirectorio inválido.\033[0m");
            System.out.flush();

            enviarInfoDirectorio(dir_actual, cliente);
            return;
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

        enviarInfoDirectorio(dir_actual, cliente);
    }

    // ------------------------------------------------------------------------
    //                              BAJAR ARCHIVO
    // ------------------------------------------------------------------------
    private static void bajarArchivo(DataInputStream inStream, SocketAddress cliente) throws Exception {
        System.out.println("\n\033[95m -- BAJAR ARCHIVO --\033[0m");

        int tam = inStream.readInt();
        byte[] bufferNombre = new byte[tam];
        inStream.read(bufferNombre);
        String nombre = new String(bufferNombre);

        System.out.println("\033[94mContenido recibido:\033[0m");
        System.out.println("\033[93mLongitud del nombre: \033[0m"+tam);
        System.out.println("\033[93mNombre del archivo: \033[0m"+nombre);

        Path filePath = dir_actual.resolve(nombre);
        if (!Files.exists(filePath)) {
            System.out.println("\033[91mArchivo no encontrado: \033[0m" + nombre);
            return;
        }

        byte[] fileData = Files.readAllBytes(filePath);
        int totalPackets = (int) Math.ceil((double) fileData.length / TAM_PAQUETE);
        int start = 0;

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream outStream = new DataOutputStream(byteOut);

        while (start < totalPackets) {
            for (int i = start; i < start + TAM_VENTANA && i < totalPackets; i++) {
                int startByte = i * TAM_PAQUETE;
                int endByte = Math.min(startByte + TAM_PAQUETE, fileData.length);
                byte[] packetData = Arrays.copyOfRange(fileData, startByte, endByte);

                outStream.writeInt(i); // Número del paquete
                outStream.writeInt(totalPackets); // Total de paquetes
                outStream.writeInt(nombre.getBytes().length); // Tamaño del nombre
                outStream.write(nombre.getBytes()); // Nombre del archivo
                outStream.writeInt(packetData.length); // Tamaño de los datos
                outStream.write(packetData); // Datos
                outStream.flush();

                ByteBuffer sendBuffer = ByteBuffer.wrap(byteOut.toByteArray());
                serverChannel.send(sendBuffer, cliente);
                byteOut.reset();

                //System.out.println("\033[93mPaquete enviado: \033[0m" + i);
            }

            // Esperar ACKs
            ByteBuffer receiveBuffer = ByteBuffer.allocate(TAM_BUFFER);
            boolean ackReceived = false;
            long startTime = System.currentTimeMillis();

            while (!ackReceived && (System.currentTimeMillis() - startTime) < TIEMPO_ESPERA_ENVIAR) {
                receiveBuffer.clear();
                SocketAddress sender = serverChannel.receive(receiveBuffer);
                if (sender != null && sender.equals(cliente)) {
                    receiveBuffer.flip();
                    DataInputStream ackStream = new DataInputStream(
                            new ByteArrayInputStream(receiveBuffer.array(), 0, receiveBuffer.limit()));
                    int ack = ackStream.readInt();
                    System.out.println("\033[92mACK recibido: \033[0m" + ack);

                    if (ack >= start) {
                        start = ack + 1; // Mover ventana
                        ackReceived = true;
                    }
                }
            }

            if (!ackReceived) {
                System.out.println("\033[91mNo se recibió ACK. Reintentando ventana desde: \033[0m" + start);
            }
        }

        System.out.println("\033[94mArchivo enviado exitosamente: \033[0m" + nombre);
    }
    
    // ------------------------------------------------------------------------
    //                             RENOMBRAR ARCHIVO
    // ------------------------------------------------------------------------
    private static void renombrarArchivo(DataInputStream inStream, SocketAddress cliente) throws Exception {
        System.out.println("\n\033[95m -- RENOMBRAR ARCHIVO / DIRECTORIO --\033[0m");
        
        int tam_original = inStream.readInt();
        byte[] bufferOriginal = new byte[tam_original];
        inStream.read(bufferOriginal);
        String original = new String(bufferOriginal);

        int tam_nuevo = inStream.readInt();
        byte[] bufferNuevo = new byte[tam_nuevo];
        inStream.read(bufferNuevo);
        String nuevo = new String(bufferNuevo);

        System.out.println("\033[94mContenido recibido:\033[0m");
        System.out.println("\033[93mLongitud del nombre original: \033[0m"+tam_original);
        System.out.println("\033[93mNombre original: \033[0m"+original);
        System.out.println("\033[93mLongitud del nuevo nombre: \033[0m"+tam_nuevo);
        System.out.println("\033[93mNuevo nombre: \033[0m"+nuevo);

        try {
            Path originalPath = dir_actual.resolve(original);
            Path nuevoPath = dir_actual.resolve(nuevo);
    
            Files.move(originalPath, nuevoPath);
            System.out.println("\033[92mArchivo renombrado con éxito.\033[0m");
        } catch (IOException e) {
            System.err.println("\033[91mError al renombrar el archivo: " + e.getMessage() + "\033[0m");
        }

        enviarInfoDirectorio(dir_actual, cliente);
    }
                    
    private static void enviarInfoDirectorio(Path p, SocketAddress direccion) throws InterruptedException {
        try {
            //TimeUnit.MILLISECONDS.sleep(500);
            // Construir el mensaje
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Encabezado del mensaje
            outStream.writeInt(1); // Tipo de mensaje: DIRECTORIO

            Path basePath = Paths.get(dir_server).normalize();
            Path hiddenPath = p.normalize();
            if (hiddenPath.startsWith(basePath)) {
                hiddenPath = basePath.relativize(hiddenPath); // Obtener el path relativo
            } else {
                System.out.println("El directorio no es un subdirectorio de dir_server");
                return;
            }
    
            byte[] pathBytes = hiddenPath.toString().getBytes();
            outStream.writeInt(pathBytes.length);
            outStream.write(pathBytes);
            System.out.println("pathBytes.length: "+pathBytes.length);
            System.out.println("pathBytes: "+hiddenPath.toString());

            // Obtener lista de archivos y directorios
            List<String> filesAndDirs = Files.list(p)
                                             .map(Path::getFileName)
                                             .map(Path::toString)
                                             .collect(Collectors.toList());
    
            // Convertir la lista en una cadena separada por comas
            String message = String.join("?", filesAndDirs);
    
            byte[] bufferContenido = message.getBytes();
            outStream.writeInt(bufferContenido.length);
            outStream.write(bufferContenido);
    
            byte[] buffer = byteOut.toByteArray();
    
            // Enviar el mensaje usando el DatagramChannel
            ByteBuffer sendBuffer = ByteBuffer.wrap(buffer);
            serverChannel.send(sendBuffer, direccion);
    
            System.out.println("Contenido del directorio enviado de forma no bloqueante.");
        } catch (IOException e) {
            System.out.println("Error al enviar el contenido del directorio: " + e.getMessage());
        }
    }
}