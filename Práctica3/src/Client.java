import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import com.google.gson.Gson;

public class Client {
    public static String direccionMulticast = "230.1.1.1";
    public static InetAddress grupo;
    public static int dgram_buf_len = 1024;

    public static int puertoMulticast = 4000;
    private static int puertoUnicast;
    private static int puertoArchivos;
    private static InetSocketAddress puertoServidor;

    public static MulticastSocket socketMulticast;
    public static DatagramSocket socketUnicast;
    public static DatagramSocket socketArchivos;

    public static String nombreUsuario;
    public static ArrayList<String> listaUsuariosActivos = new ArrayList<>();
    
    final public static int TAM_VENTANA = 5;
    final public static int TAM_PAQUETE = 10000;
    final public static int TAM_BUFFER = 65535;
    final static int TIEMPO_ESPERA_ENVIAR = 500;
    final static int TIEMPO_ESPERA_RECIBIR = 2000;

    static String nombreArchivo = "";
    static String rutaArchivo = "";
        
    public Client(String nombreUsuario) {
        Client.nombreUsuario = nombreUsuario;
    }
    
    public static void main(String[] args) {
        try {
            int z = 0;
            // BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "ISO-8859-1"));
    
            // Lista de todas las interfaces en red
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                System.out.print("[Interfaz " + ++z + "]: ");
                despliegaInfoNIC(netint); 
            }
    
            // System.out.print("\nElige la interfaz multicast (número): ");
            // int interfaz = Integer.parseInt(br.readLine());
    
            // Obtener la interfaz seleccionada
            // NetworkInterface ni = NetworkInterface.getByIndex(interfaz);
            // if (ni == null) {
            //     System.out.println("La interfaz seleccionada no es válida.");
            //     return;
            // }
    
            // System.out.println("Usando la interfaz: " + ni.getDisplayName());
            // Conexión multicast
            grupo = InetAddress.getByName(direccionMulticast);
            socketMulticast = new MulticastSocket(puertoMulticast);
            // Conexión socket unicast mensajes privados
            puertoUnicast = 5000 + new Random().nextInt(1000);
            socketUnicast = new DatagramSocket(puertoUnicast);
            // Conexión socket unicast archivos
            puertoArchivos = 6000 + new Random().nextInt(1000);
            socketArchivos = new DatagramSocket(puertoArchivos);

            // Unión al grupo multicast
            socketMulticast.joinGroup(grupo);
    
            System.out.println("\033[92mCliente unido al grupo " + grupo + " en el puerto " + puertoMulticast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto único " + puertoUnicast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto para enviar archivos " + puertoArchivos + ".\033[0m");
            // Mensaje de inicio de sesión, se manda el puerto unicast del usuario
            Mensaje mensajeInicio = new Mensaje("inicioSesion", nombreUsuario, Integer.toString(puertoUnicast), Integer.toString(puertoArchivos));
            String jsonInicio = mensajeInicio.toJson();
            DatagramPacket paqueteInicio = new DatagramPacket(jsonInicio.getBytes(StandardCharsets.UTF_8), jsonInicio.length(), grupo, puertoMulticast);
            socketMulticast.send(paqueteInicio);

        } catch (UnsupportedEncodingException e) {
            System.err.println("\033[91mEl encoding especificado no es compatible:\033[0m " + e.getMessage());
        } catch (SocketException e) {
            System.err.println("\033[91mError al trabajar con sockets:\033[0m " + e.getMessage());
        } catch (IOException e) {
            System.err.println("\033[91mError de entrada/salida:\033[0m " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("\033[91mEl valor ingresado no es un número válido:\033[0m " + e.getMessage());
        }
    }
    
    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                           RECIBIR MENSAJES MULTICAST
    * 
    * ------------------------------------------------------------------------------------------------------  */
    static void recibirMensajesMulticast(MulticastSocket socketMulticast, Interfaz interfaz) {
        for(;;) {
            try {
                byte[] buf = new byte[dgram_buf_len];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socketMulticast.receive(recv);
                String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                
                if (esJsonValido(jsonMensaje)) {
                    Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                    switch (mensaje.getTipo()) {
                        case "usuariosActivosInicio":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + " se unió al chat");
                            interfaz.mostrarMensaje("Lista de usuarios activos: " + mensaje.getContenido()+ "\n");
                            System.out.println(mensaje.getUsuario() + "\033[92m se unió al chat\033[0m");
                            System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                            // Obtener puerto unicast del Servidor
                            int puertoS = Integer.parseInt(mensaje.getDestinatario());
                            puertoServidor = new InetSocketAddress(recv.getAddress(), puertoS);
                            System.out.print("\033[95mPuerto del servidor: \033[0m" + puertoServidor.getPort() + " \033[95mcon dirección\033[0m " + puertoServidor.getAddress());
                            obtenerUsuariosActivos(mensaje, socketMulticast);
                            interfaz.actualizarListaUsuarios();
                            break;
                        
                        case "usuariosActivosCierre":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + " salió del chat");
                            interfaz.mostrarMensaje("Lista de usuarios activos: " + mensaje.getContenido()+ "\n");
                            System.out.println(mensaje.getUsuario() + "\033[92m salió del chat\033[0m");
                            System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                            obtenerUsuariosActivos(mensaje, socketMulticast);
                            interfaz.actualizarListaUsuarios();
                            break;

                        case "mensajeGrupal":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + ": " + mensaje.getContenido());
                            System.out.println("\033[95mMensaje de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                            break;
                        
                        case "mensajeArchivo":
                            interfaz.mostrarMensaje("El usuario " + mensaje.getUsuario() + " envió un archivo");
                            break;

                        default:
                            break;
                    }
                }else {
                    // Si no es JSON, se asume que son fragmentos binarios
                    System.out.println("Fragmento recibido: " + recv.getLength() + " bytes.");
                }
            }catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
    
    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                      RECIBIR MENSAJES UNICAST
    * 
    * ------------------------------------------------------------------------------------------------------  */
    static void recibirMensajesUnicast(DatagramSocket socketUnicast, Interfaz interfaz) {
        for (;;) {
            try {
                byte[] buf = new byte[dgram_buf_len];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socketUnicast.receive(recv);
                String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                System.out.println("\033[93mDatos recibidos: \033[0m" + jsonMensaje);

                if (esJsonValido(jsonMensaje)) {
                    Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                    switch (mensaje.getTipo()) {
                        case "mensajePrivado":
                            interfaz.mostrarMensaje("Mensaje privado de " + mensaje.getUsuario() + ": " + mensaje.getContenido());
                            System.out.println("\033[94mMensaje privado de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                            break;
                        
                        case "mensajeConfirmacion":
                            System.out.println("Se envío el mensaje privado a " + mensaje.getDestinatario());
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
    }
    
    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                          ENVIAR MENSAJE GRUPAL
    * 
    * ------------------------------------------------------------------------------------------------------  */
    public static void enviarMensajeGrupal(String nombreUsuario, String mensajeGrupal, MulticastSocket socketMulticast) {
        try {
            Mensaje mensaje = new Mensaje("mensajeGrupal", nombreUsuario, mensajeGrupal, null);
            String jsonMensaje = mensaje.toJson();
            byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
            socketMulticast.send(paqueteMensaje);
        }catch (IOException e) {
            System.err.println("\033[91mError al enviar mensaje:\033[0m " + e.getMessage());
        }
    }
    
    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                          ENVIAR MENSAJE PRIVADO
    * 
    * ------------------------------------------------------------------------------------------------------  */
    public static void enviarMensajePrivado(String nombreUsuario, String mensajePrivado, String destinatario, MulticastSocket socketMulticast) {
        try {
            Mensaje mensaje = new Mensaje("direccionDestinatario", nombreUsuario, mensajePrivado, destinatario);
            String jsonMensaje = mensaje.toJson();
            byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
            socketMulticast.send(paqueteMensaje);
        }catch (IOException e) {
            System.err.println("\033[91mError al enviar mensaje:\033[0m " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                         ENVIAR SOLICITUD DE ARCHIVO
    *  -- Este es un primer mensaje para el servidor para que se prepare para recibir los datos del archivo
    * ------------------------------------------------------------------------------------------------------  */
    public static void enviarSolicitudArchivo(String nombreUsuario, File selectedFile, MulticastSocket socketMulticast) {
        try {
            Mensaje mensajeArchivo = new Mensaje("solicitudEnviarArchivo", nombreUsuario, null, null);
            String jsonArchivo = mensajeArchivo.toJson();
            DatagramPacket paqueteMetadatos = new DatagramPacket(jsonArchivo.getBytes(StandardCharsets.UTF_8), jsonArchivo.length(), grupo, puertoMulticast);
            socketMulticast.send(paqueteMetadatos);
            enviarArchivo(nombreUsuario, selectedFile);
        }catch (IOException e) {
            System.err.println("\033[91mError al enviar mensaje:\033[0m " + e.getMessage());
        }
    }
    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                               ENVIAR ARCHIVO
    * 
    * ------------------------------------------------------------------------------------------------------  */
    public static void enviarArchivo(String nombreUsuario, File selectedFile) {
        try {
            // Clases para enviar información
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteOut);

            // Clases para recibir inormación
            ByteArrayInputStream byteIn;
            DataInputStream inStream;

            DatagramPacket packet;

            rutaArchivo = selectedFile.getAbsolutePath();
            nombreArchivo = selectedFile.getName();

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
    *                                         OBTENER USUARIOS ACTIVOS
    * 
    * ------------------------------------------------------------------------------------------------------  */
    public static void obtenerUsuariosActivos(Mensaje mensaje, MulticastSocket socketMulticast) {
        String contenido = mensaje.getContenido();
        String[] usuariosActivos = contenido.split(",");
        listaUsuariosActivos.clear();

        for (String usuario : usuariosActivos) {
            listaUsuariosActivos.add(usuario.trim());
        }
        System.out.println("\033[93mUsuarios activos actualizados:\033[0m " + listaUsuariosActivos);
    }

    /* ------------------------------------------------------------------------------------------------------
    *                       
    *                                      VALIDAR SI EL MENSAJE ES UN JSON
    * 
    * ------------------------------------------------------------------------------------------------------  */
    private static boolean esJsonValido(String json) {
        try {
            new com.google.gson.JsonParser().parse(json);
            return true;
        } catch (com.google.gson.JsonSyntaxException e) {
            return false;
        }
    }
    
    static void despliegaInfoNIC(NetworkInterface netint) throws SocketException {
        System.out.printf("Nombre de despliegue: %s\n", netint.getDisplayName());
        System.out.printf("Nombre: %s\n", netint.getName());
        
        String multicast = (netint.supportsMulticast())?"Soporta multicast":"No soporta multicast";
        
        System.out.printf("Multicast: %s\n", multicast);
        
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("Direccion: %s\n", inetAddress);
        }
        
        System.out.printf("\n");
    }

    /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                             CIERRE SESIÓN
     * 
     * ------------------------------------------------------------------------------------------------------  */
    public static void salirServidor(String nombreUsuario, MulticastSocket socketMulticast, InetAddress grupo) {
        try {
            Mensaje mensaleSalir = new Mensaje("cierreSesion", nombreUsuario, null, null);
            String jsonSalir = mensaleSalir.toJson();
            DatagramPacket paqueteSalida = new DatagramPacket(jsonSalir.getBytes(StandardCharsets.UTF_8), jsonSalir.length(), grupo, puertoMulticast);
            socketMulticast.send(paqueteSalida);

            socketMulticast.leaveGroup(grupo);
            socketMulticast.close();
            socketUnicast.close();
            System.out.println("\033[92mConexión cerrada. Has salido del chat\033[0m");
            System.exit(0);
        }catch (IOException e) {
            System.err.println("\033[91mError al salir del grupo:\033[0m " + e.getMessage());
        }
    }
}
