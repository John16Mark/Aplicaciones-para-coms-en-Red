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
import com.google.gson.Gson;

public class Cliente extends Thread{
    public static String direccionMulticast = "230.1.1.1";
    public static int puertoMulticast = 4000;
    public static int dgram_buf_len = 1024;
    
    private String nombreUsuario;
    private int puertoUnicast;
     
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

        try (MulticastSocket socketMulticast = new MulticastSocket(puertoMulticast);
            DatagramSocket socketUnicast = new DatagramSocket(puertoUnicast)) {
            // Unirse al servidor multicast
            socketMulticast.joinGroup(grupo);
    
            System.out.println("\033[92mCliente unido al grupo " + grupo + " en el puerto " + puertoMulticast + ".\033[0m");
            System.out.println("\033[92mCliente con puerto único " + puertoUnicast + ".\033[0m");
            
            // Mensaje de inicio de sesión, se manda el puerto unicast del usuario
            Mensaje mensajeInicio = new Mensaje("inicioSesion", nombreUsuario, Integer.toString(puertoUnicast), null);
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
                System.out.println("4. Ver archivos grupales");
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
                     *                                  ENVIAR ARCHIVO -- cambiar
                     * --------------------------------------------------------------------------------------- */
                    case "3":  
                        System.out.println("Selecciona el archivo que deseas enviar:");
                        String rutaArchivo = scanner.nextLine().trim();
                        File archivo = new File(rutaArchivo);

                        if (archivo.exists()) {
                            try (FileInputStream fis = new FileInputStream(archivo)) {
                                byte[] fileBytes = fis.readAllBytes();
                                int fragmentSize = 10240;
                                int totalFragments = (int) Math.ceil((double) fileBytes.length / fragmentSize);

                                Mensaje mensajeArchivo = new Mensaje("archivo", nombreUsuario, archivo.getName(), String.valueOf(totalFragments));
                                String jsonArchivo = mensajeArchivo.toJson();
                                DatagramPacket paqueteMetadatos = new DatagramPacket(jsonArchivo.getBytes(StandardCharsets.UTF_8), jsonArchivo.length(), grupo, puertoMulticast);
                                socketMulticast.send(paqueteMetadatos);

                                for (int i = 0; i < totalFragments; i++) {
                                    int start = i * fragmentSize;
                                    int end = Math.min(fileBytes.length, start + fragmentSize);
                                    byte[] fragment = Arrays.copyOfRange(fileBytes, start, end);

                                    DatagramPacket paqueteFragmento = new DatagramPacket(fragment, fragment.length, grupo, puertoMulticast);
                                    socketMulticast.send(paqueteFragmento);
                                    System.out.println("Enviando fragmento " + (i + 1) + " de " + totalFragments);
                                }

                                System.out.println("Archivo enviado correctamente");

                            } catch (IOException e) {
                                System.out.println("Error al leer o enviar el archivo: " + e.getMessage());
                            }
                        } else {
                            System.out.println("El archivo no existe");
                        }
                        break;
                    /* ---------------------------------------------------------------------------------------
                     *                            VER Y DESCARGAR ARCHIVOS RECIBIDOS -- cambiar
                     * --------------------------------------------------------------------------------------- */
                    case "4":
                        File dir = new File("recibidos");

                        if (dir.exists() && dir.isDirectory()) {
                            File[] archivos = dir.listFiles();

                            if (archivos != null && archivos.length > 0) {
                                System.out.println("Archivos disponibles para descargar:");
                                for (File archivoDisponible : archivos) {
                                    System.out.println(archivoDisponible.getName());
                                }
                            }else {
                                System.out.println("No hay archivos disponibles");
                            }
                        }
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

            scanner.close();
            System.out.println("\033[92mConexión cerrada\033[0m");
        }catch (IOException e) {
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