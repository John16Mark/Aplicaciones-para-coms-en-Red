import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Scanner;

import javax.swing.JOptionPane;

import com.google.gson.Gson;

public class Client {
    public static final String direccionMulticast = "230.1.1.1";
    public static final int puertoMulticast = 4000;
    public static final int dgram_buf_len = 1024;
    public static InetAddress grupo;
    public static MulticastSocket socket;
    public static String nombreUsuario;
    
    public Client(String nombreUsuario) {
        Client.nombreUsuario = nombreUsuario;
    }

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
    
            grupo = InetAddress.getByName(direccionMulticast);
            socket = new MulticastSocket(puertoMulticast);
            // SocketAddress dirm = new InetSocketAddress(grupo, puertoMulticast);
            
            // Unión al grupo multicast
            socket.joinGroup(grupo);
    
            System.out.println("\033[92mCliente unido al grupo " + grupo + " en el puerto " + puertoMulticast + ".\033[0m");
    
            Mensaje mensajeInicio = new Mensaje("inicioSesion", nombreUsuario, null, null);
            String jsonInicio = mensajeInicio.toJson();
            DatagramPacket paqueteInicio = new DatagramPacket(jsonInicio.getBytes(StandardCharsets.UTF_8), jsonInicio.length(), grupo, puertoMulticast);
            socket.send(paqueteInicio);

        } catch (UnsupportedEncodingException e) {
            System.err.println("El encoding especificado no es compatible: " + e.getMessage());
        } catch (SocketException e) {
            System.err.println("Error al trabajar con sockets: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error de entrada/salida: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("El valor ingresado no es un número válido: " + e.getMessage());
        }
    }

    static void recibirMensajes(MulticastSocket socket, Interfaz interfaz) {
        for(;;) {
            try {
                byte[] buf = new byte[dgram_buf_len];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                socket.receive(recv);
                String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                
                if (esJsonValido(jsonMensaje)) {
                    Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                    switch (mensaje.getTipo()) {
                        case "usuariosActivosInicio":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + " se unió al chat");
                            interfaz.mostrarMensaje("Lista de usuarios activos: " + mensaje.getContenido()+ "\n");
                            System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                            break;
                        
                        case "usuariosActivosCierre":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + " salió del chat");
                            interfaz.mostrarMensaje("Lista de usuarios activos: " + mensaje.getContenido()+ "\n");
                            System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                            break;

                        case "mensajeGrupal":
                            interfaz.mostrarMensaje(mensaje.getUsuario() + ": " + mensaje.getContenido());
                            break;

                        case "mensajePrivado":
                            System.out.println("\033[93mMensaje privado de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
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
     *                                          ENVIAR MENSAJE GRUPAL
     * 
     * ------------------------------------------------------------------------------------------------------  */
    public static void enviarMensajeGrupal(String nombreUsuario, String mensajeGrupal, MulticastSocket socket) {
        try {
            Mensaje mensaje = new Mensaje("mensajeGrupal", nombreUsuario, mensajeGrupal, null);
            String jsonMensaje = mensaje.toJson();
            byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
            socket.send(paqueteMensaje);
        }catch (IOException e) {
            System.err.println("Error al enviar mensaje: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                          ENVIAR MENSAJE PRIVADO
     * 
     * ------------------------------------------------------------------------------------------------------  */
    public static void enviarMensajePrivado(String nombreUsuario, String mensajeGrupal, MulticastSocket socket) {
        try {
            Mensaje mensaje = new Mensaje("mensajeGrupal", nombreUsuario, mensajeGrupal, null);
            String jsonMensaje = mensaje.toJson();
            byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
            socket.send(paqueteMensaje);
        }catch (IOException e) {
            System.err.println("Error al enviar mensaje: " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------------------------------------------
     *                       
     *                                             CIERRE SESIÓN
     * 
     * ------------------------------------------------------------------------------------------------------  */
    public static void salirServidor(String nombreUsuario, MulticastSocket socket, InetAddress grupo) {
        try {
            Mensaje mensaleSalir = new Mensaje("cierreSesion", nombreUsuario, null, null);
            String jsonSalir = mensaleSalir.toJson();
            DatagramPacket paqueteSalida = new DatagramPacket(jsonSalir.getBytes(StandardCharsets.UTF_8), jsonSalir.length(), grupo, puertoMulticast);
            socket.send(paqueteSalida);

            socket.leaveGroup(grupo);
            socket.close();
            System.out.println("Conexión cerrada. Has salido del chat");
        }catch (IOException e) {
            System.err.println("Error al salir del grupo: " + e.getMessage());
        }
    }
}
