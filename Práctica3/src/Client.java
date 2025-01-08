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

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.google.gson.Gson;

public class Client {
    public static String direccionMulticast = "230.1.1.1";
    public static String nombreUsuario;
    public static int puertoMulticast = 4000;
    public static int dgram_buf_len = 1024;
    public static InetAddress grupo;
    public static MulticastSocket socketMulticast;
    public static DatagramSocket socketUnicast;
    public static ArrayList<String> listaUsuariosActivos = new ArrayList<>();
    
    private static int puertoUnicast;
        
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
            // Conexión unicast
            puertoUnicast = 5000 + new Random().nextInt(1000);
            socketUnicast = new DatagramSocket(puertoUnicast);
            // SocketAddress dirm = new InetSocketAddress(grupo, puertoMulticast);
            
            // Unión al grupo multicast
            socketMulticast.joinGroup(grupo);
    
            System.out.println("\033[92mCliente unido al grupo " + grupo + " en el puerto " + puertoMulticast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto único " + puertoUnicast + ".\033[0m");
            // Mensaje de inicio de sesión, se manda el puerto unicast del usuario
            Mensaje mensajeInicio = new Mensaje("inicioSesion", nombreUsuario, Integer.toString(puertoUnicast), null);
            String jsonInicio = mensajeInicio.toJson();
            DatagramPacket paqueteInicio = new DatagramPacket(jsonInicio.getBytes(StandardCharsets.UTF_8), jsonInicio.length(), grupo, puertoMulticast);
            socketMulticast.send(paqueteInicio);

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

                if (esJsonValido(jsonMensaje)) {
                    Mensaje mensaje = Mensaje.fromJson(jsonMensaje);

                    switch (mensaje.getTipo()) {
                        case "mensajePrivado":
                            interfaz.mostrarMensaje("Mensaje privado de " + mensaje.getUsuario() + ": " + mensaje.getContenido());
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
    *                                               ENVIAR ARCHIVO
    * 
    * ------------------------------------------------------------------------------------------------------  */
    public static void enviarArchivo(String nombreUsuario, MulticastSocket socketMulticast) {
        JFrame frame = new JFrame();
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        frame.toFront();

        JFileChooser fileChooser = new JFileChooser();
        int seleccion = fileChooser.showOpenDialog(frame);
        frame.dispose();

        if (seleccion == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            String nombreArchivo = archivo.getName();
            long archivoSize = archivo.length();

            Mensaje mensaje = new Mensaje("archivo", nombreUsuario, nombreArchivo, String.valueOf(archivoSize));
            String jsonArchivo = mensaje.toJson();
            DatagramPacket metadatosPaquete = new DatagramPacket(jsonArchivo.getBytes(StandardCharsets.UTF_8), jsonArchivo.length(), grupo, puertoMulticast);

            try {
                socketMulticast.send(metadatosPaquete);
                System.out.println("Enviando archivo... " + nombreArchivo + " [" + archivoSize + " bytes]");

                FileInputStream fis = new FileInputStream(archivo);
                byte[] buffer = new byte[dgram_buf_len];
                int bytesRead;
                int sequenceNumber = 0;
                int windowSize = 10;
                List<DatagramPacket> ArchivoEnPackets = new ArrayList<>();

                while ((bytesRead = fis.read(buffer)) != -1) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(baos);
                    out.writeInt(sequenceNumber);
                    out.write(buffer, 0, bytesRead);
                    byte[] packetData = baos.toByteArray();

                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, grupo, puertoMulticast);
                    ArchivoEnPackets.add(packet);

                    sequenceNumber++;
                }

                System.out.println("Archivo dividido en " + ArchivoEnPackets.size() + " partes.");
                int vueltasEnvio = ArchivoEnPackets.size() / windowSize;

                for (int i = 0; i < vueltasEnvio; i++) {
                    for (int j = 0; j < windowSize && (i * windowSize + j) < ArchivoEnPackets.size(); j++) {
                        int packetIndex = i * windowSize + j;
                        socketMulticast.send(ArchivoEnPackets.get(packetIndex));
                    }
                }

                int paquetesRestantes = ArchivoEnPackets.size() % windowSize;
                int startIndex = vueltasEnvio * windowSize;

                if (paquetesRestantes > 0) {
                    for (int i = 0; i < paquetesRestantes; i++) {
                        socketMulticast.send(ArchivoEnPackets.get(startIndex + i));
                    }
                }

                System.out.println("Archivo enviado completamente");
                fis.close();
            }catch (IOException e) {
                System.err.println("Error al enviar el archivo: " + e.getMessage());
            }
        }else {
            System.out.println("No se seleccionó ningún archivo.");
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
        }catch (IOException e) {
            System.err.println("\033[91mError al salir del grupo:\033[0m " + e.getMessage());
        }
    }
}
