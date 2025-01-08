import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ServidorNoBloqueante {

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static int PORT = 5555;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    static String dir_server;
    static Path dir_actual;

    static DatagramChannel serverChannel;

    enum Estado {
        ESPERANDO_SYN,
        ESPERANDO_ACK,
        CONEXION_ESTABLECIDA,
        CREAR_DIRECTORIO,
        ELIMINAR_ARCHIVO,
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
                                System.out.println("\033[94mPaquete recibido en estado de conexión establecida.\033[0m");

                                int instruccion = inStream.readInt();
                                switch (instruccion) {
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
                                case -7:
                                    System.out.println("\033[92mRecibido código para crear renombrar archivo/directorio\033[0m");
                                    System.out.flush();
                                    renombrarArchivo(inStream, cliente);
                                    break;
                                default:
                                    break;
                                }
                                
                                break;
                            case CREAR_DIRECTORIO:
                                if (!cliente.equals(clienteActual)) {
                                    System.out.println("\033[31mPaquete recibido de cliente desconocido\033[0m");
                                    continue;
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
                    
    private static void enviarInfoDirectorio(Path p, SocketAddress direccion) {
        try {
            // Construir el mensaje
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