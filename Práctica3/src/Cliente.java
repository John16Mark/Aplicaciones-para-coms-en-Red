import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import javax.swing.JFileChooser;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Cliente extends Thread{
    public static String direccionMulticast = "230.1.1.1";
    public static int puertoMulticast = 4000;
    public static int dgram_buf_len = 1024;
    
    private String nombreUsuario;
    private int puertoUnicast;
    private int puertoArchivos;
    private static InetSocketAddress puertoServidor;

    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    static String nombreArchivo = "";
    static String rutaArchivo = "";
     
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce tu nombre de usuario: ");
        String nombreUsuario = scanner.nextLine().trim();
        new Cliente(nombreUsuario).start();
    }

    public Cliente(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public void run () {
        InetAddress grupo = null;

        try {
            grupo = InetAddress.getByName(direccionMulticast);
        } catch ( UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        puertoUnicast = 5000 + new Random().nextInt(1000);
        puertoArchivos = 6000 + new Random().nextInt(1000);

        try (MulticastSocket socketMulticast = new MulticastSocket(puertoMulticast);
            DatagramSocket socketUnicast = new DatagramSocket(puertoUnicast);
            DatagramSocket socketArchivos = new DatagramSocket(puertoArchivos)) {
            // Unirse al servidor multicast
            socketMulticast.joinGroup(grupo);
    
            System.out.println("\033[92mCliente unido al grupo " + grupo + " en el puerto " + puertoMulticast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto único " + puertoUnicast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto para enviar archivos " + puertoArchivos + ".\033[0m");
            
            // Mensaje de inicio de sesión, se manda el puerto unicast del usuario
            Mensaje mensajeInicio = new Mensaje("inicioSesion", nombreUsuario, Integer.toString(puertoUnicast), Integer.toString(puertoArchivos));
            String jsonInicio = mensajeInicio.toJson();
            DatagramPacket paqueteInicio = new DatagramPacket(jsonInicio.getBytes(StandardCharsets.UTF_8), jsonInicio.length(), grupo, puertoMulticast);
            socketMulticast.send(paqueteInicio);

            Thread recibirMensajesMulticast = new Thread(() -> {
                for (;;) {
                    try {
                        byte[] buf = new byte[dgram_buf_len];
                        DatagramPacket recv = new DatagramPacket(buf, buf.length);
                        socketMulticast.receive(recv);
                        String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
    
                        if (esJsonValido(jsonMensaje)) {
                            Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                            switch (mensaje.getTipo()) {
                                case "usuariosActivosInicio":
                                    System.out.println(mensaje.getUsuario() + "\033[92m se unió al chat\033[0m");
                                    System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                                    int puertoS = Integer.parseInt(mensaje.getDestinatario());
                                    puertoServidor = new InetSocketAddress(recv.getAddress(), puertoS);
                                    System.out.print("\033[95mPuerto del servidor: \033[0m" + puertoServidor.getPort() + " \033[95mcon dirección\033[0m " + puertoServidor.getAddress());
                                    break;
                                
                                case "usuariosActivosCierre":
                                    System.out.println(mensaje.getUsuario() + "\033[92m salió del chat\033[0m");
                                    System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                                    break;

                                case "mensajeGrupal":
                                    System.out.println("\033[95mMensaje de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                                    break;
                                default:
                                    break;
                            }
                        }else {
                            // Si no es JSON, asumimos que son datos binarios (como un fragmento de archivo)
                            System.out.println("Fragmento recibido: " + recv.getLength() + " bytes.");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            });

            Thread recibirMensajesUnicast = new Thread(() -> {
                for (;;) {
                    try {
                        byte[] buf = new byte[dgram_buf_len];
                        DatagramPacket recv = new DatagramPacket(buf, buf.length);
                        socketUnicast.receive(recv);
                        String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
    
                        if (esJsonValido(jsonMensaje)) {
                            Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                            switch (mensaje.getTipo()) {
                                case "mensajePrivado":
                                    System.out.println("\033[94mMensaje privado de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                                    break;
                                
                                case "mensajeConfirmacion":
                                    String[] partes = mensaje.getDestinatario().split(":");
                                    String direccion = partes[0].trim();
                                    if (direccion.startsWith("/")) {
                                        direccion = direccion.substring(1);
                                    }
                                    int puerto = Integer.parseInt(partes[1]);
                                    InetAddress inetAddress = InetAddress.getByName(direccion);
                                    
                                    InetSocketAddress direccionDestino = new InetSocketAddress(inetAddress, puerto);
                                    String mensajePrivado = new Mensaje("mensajePrivado", mensaje.getUsuario(), mensaje.getContenido(), null).toJson();
                                    DatagramPacket paquetePrivado = new DatagramPacket(mensajePrivado.getBytes(StandardCharsets.UTF_8), mensajePrivado.length(), direccionDestino.getAddress(), direccionDestino.getPort());
                                    try{
                                        socketMulticast.send(paquetePrivado);
                                    }catch (IOException e) {
                                        System.err.println("\033[91mError al mandar mensaje: \033[0m" + e.getMessage());
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }else {
                            // Si no es JSON, asumimos que son datos binarios (como un fragmento de archivo)
                            System.out.println("Fragmento recibido: " + recv.getLength() + " bytes.");
                        }
                    }catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            });

            // Thread enviarArchivos = new Thread(() -> {
            //     for(;;) {
            //         try {

            //         }
            //     }
            // });

            recibirMensajesMulticast.start();
            recibirMensajesUnicast.start();

            // Menú para enviar mensajes
            Scanner scanner = new Scanner(System.in);
            boolean continuar = true;

            while (continuar) {
                System.out.println("\n\033[96mMenu");
                System.out.println("1. Enviar mensaje grupal");
                System.out.println("2. Enviar mensaje privado");
                System.out.println("3. Enviar un archivo");
                System.out.println("5. Salir del servidor");
                System.out.println("Seleccione una opcion: \033[0m");
                String opcion = scanner.nextLine().trim();

                switch (opcion) {
                    /* ---------------------------------------------------------------------------------------
                     *                                  ENVIAR MENSAJE GRUPAL
                     * --------------------------------------------------------------------------------------- */
                    case "1": 
                        System.out.println("Escribe tu mensaje grupal: ");
                        String contenidoMensaje = scanner.nextLine();
                        
                        Mensaje mensaje = new Mensaje("mensajeGrupal", nombreUsuario, contenidoMensaje, null);
                        String jsonMensaje = mensaje.toJson();
                        byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);

                        if (mensajeBytes.length > Cliente.dgram_buf_len) {
                            System.err.println("El mensaje es muy largo para enviarlo");
                            continue;
                        }

                        DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
                        socketMulticast.send(paqueteMensaje);
                        break;
                    /* ---------------------------------------------------------------------------------------
                     *                                  ENVIAR MENSAJE PRIVADO
                     * --------------------------------------------------------------------------------------- */
                    case "2":
                        System.out.println("¿A quién deseas mandarle un mensaje privado?");
                        String destinatario = scanner.nextLine().trim();
                        System.out.println("Escribe tu mensaje:");
                        String contenido = scanner.nextLine().trim();
                    
                        Mensaje mensajePrivado = new Mensaje("direccionDestinatario", nombreUsuario, contenido, destinatario);
                        obtenerDireccionDestinatario(mensajePrivado, grupo, socketUnicast, socketMulticast);
                        break;
                    /* ---------------------------------------------------------------------------------------
                     *                                    ENVIAR ARCHIVO
                     * --------------------------------------------------------------------------------------- */
                    case "3":
                        Mensaje mensajeArchivo = new Mensaje("solicitudEnviarArchivo", nombreUsuario, null, null);
                        String jsonArchivo = mensajeArchivo.toJson();
                        DatagramPacket paqueteMetadatos = new DatagramPacket(jsonArchivo.getBytes(StandardCharsets.UTF_8), jsonArchivo.length(), grupo, puertoMulticast);
                        socketMulticast.send(paqueteMetadatos);
                        enviarArchivo(socketArchivos);
                        break;
                    /* ---------------------------------------------------------------------------------------
                     *                               SALIR DEL CHAT MULTICAST
                     * --------------------------------------------------------------------------------------- */
                    case "5":
                        Mensaje salir = new Mensaje("cierreSesion", nombreUsuario, null, null);
                        String jsonSalir = salir.toJson();
                        DatagramPacket paqueteSalida = new DatagramPacket(jsonSalir.getBytes(StandardCharsets.UTF_8), jsonSalir.length(), grupo, puertoMulticast);
                        socketMulticast.send(paqueteSalida);
                        continuar = false;
                        break;
                    default:
                        break;
                }
            }

            recibirMensajesMulticast.interrupt();
            recibirMensajesUnicast.interrupt();

            socketMulticast.leaveGroup(grupo);
            socketMulticast.close();
            socketUnicast.close();
            socketArchivos.close();

            scanner.close();
            System.out.println("\033[92mConexión cerrada\033[0m");
        }catch (IOException e) {
            e.printStackTrace();
        }        
    }
     /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                              ENVIAR ARCHIVO
     * 
     * ------------------------------------------------------------------------------------------------------  */
    static void enviarArchivo(DatagramSocket socketArchivos) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramPacket packet;

            Scanner ruta = new Scanner(System.in);
            System.out.println("Ingresa la ruta del archivo: ");
            String rutaArchivo = ruta.nextLine();

            // Crear el objeto File
            File archivo = new File(rutaArchivo);

            // Verificar si el archivo existe
            if (!archivo.exists()) {
                System.out.println("El archivo no existe");
                return;
            }
            ruta.close();
            nombreArchivo = archivo.getName();
            // JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            // int returnValue = jfc.showOpenDialog(null);
            // if (returnValue == JFileChooser.APPROVE_OPTION) {
            //     File selectedFile = jfc.getSelectedFile();
            //     String rutaArchivo = selectedFile.getAbsolutePath();
            //     nombreArchivo = selectedFile.getName();
            // } else
            //     return;
            
            byte[] file = Files.readAllBytes(archivo.toPath());
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
                    packet = new DatagramPacket(bufferOut, bufferOut.length, puertoServidor.getAddress(), puertoServidor.getPort());
                    socketArchivos.send(packet);
                    byteOut.reset();
                        apuntador++;
                }

                try {
                    // Recibir el ACK
                    socketArchivos.setSoTimeout(TIEMPO_ESPERA_ENVIAR);
                    byte[] buffer = new byte[TAM_BUFFER];
                    packet = new DatagramPacket(buffer, buffer.length);
                    socketArchivos.receive(packet);
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
        }catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                              VALIDAR JSON
     * 
     * ------------------------------------------------------------------------------------------------------  */
    private boolean esJsonValido(String json) {
        try {
            new com.google.gson.JsonParser().parse(json);
            return true;
        } catch (com.google.gson.JsonSyntaxException e) {
            return false;
        }
    }

    /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                  OBTENER DIRECCIÓN DEL DESTINATARIO
     * 
     * ------------------------------------------------------------------------------------------------------  */
    private void obtenerDireccionDestinatario(Mensaje mensaje, InetAddress grupo, DatagramSocket socketUnicast, MulticastSocket socketMulticast) {
        try {
            String jsonMensaje = mensaje.toJson();
            byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
            socketMulticast.send(paqueteMensaje);
        } catch (IOException e) {
            System.err.println("Error al obtener dirección del destinatario: " + e.getMessage());
        }
    }   
}