import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
        CONEXION_ESTABLECIDA
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
                                dir(dir_actual, cliente);
                                break;
                            case CONEXION_ESTABLECIDA:
                                if (!cliente.equals(clienteActual)) {
                                    System.out.println("\033[31mPaquete recibido de cliente desconocido\033[0m");
                                    continue;
                                }
                                // Aquí manejarás los paquetes de datos regulares.
                                System.out.println("\033[94mPaquete recibido en estado de conexión establecida.\033[0m");
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
                    
    void enviarInfoDirectorio() {

    }
                    
    static void dir(Path p, SocketAddress direccion) {
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
            System.out.println("bufferContenido.length: "+bufferContenido.length);
            System.out.println("bufferContenido: "+message);
    
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