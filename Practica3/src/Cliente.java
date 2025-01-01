import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import com.google.gson.Gson;

public class Cliente extends Thread {
    public static final String direccionMulticast = "230.1.1.1";
    public static final int puertoMulticast = 4000;
    public static final int dgram_buf_len = 1024;
    private String nombreUsuario;
    private int puertoCliente;

    public Cliente (String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        // this.puertoCliente = puertoCliente;
    }
    
    private boolean esJsonValido(String json) {
        try {
            new com.google.gson.JsonParser().parse(json);
            return true;
        } catch (com.google.gson.JsonSyntaxException e) {
            return false;
        }
    }

    public void run () {
        InetAddress grupo = null;

        try {
            grupo = InetAddress.getByName(direccionMulticast);
        } catch ( UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try (MulticastSocket socket = new MulticastSocket(puertoMulticast)) {
            // El cliente se une al servidor
            socket.joinGroup(grupo);
            System.out.println("\033[92mCliente unido al grupo multicast.\033[0m");
            // DatagramSocket socketPrivado = new DatagramSocket(puertoCliente);
            // Enviar mensaje de inicio
            Mensaje mensajeInicio = new Mensaje("inicio", nombreUsuario, "", null);
            String jsonInicio = mensajeInicio.toJson();
            DatagramPacket paqueteInicio = new DatagramPacket(jsonInicio.getBytes(StandardCharsets.UTF_8), jsonInicio.length(), grupo, puertoMulticast);
            socket.send(paqueteInicio);

            // Hilo para recibir los mensajes
            Thread recibirMensajes = new Thread(() -> {
                for (;;) {
                    try {
                        byte[] buf = new byte[dgram_buf_len];
                        DatagramPacket recv = new DatagramPacket(buf, buf.length);
                        
                        socket.receive(recv);
                        
                        String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                        if (esJsonValido(jsonMensaje)) {
                            Mensaje mensaje = Mensaje.fromJson(jsonMensaje);
                        // String mensaje = new String(recv.getData(), 0, recv.getLength());
                        // System.out.print("\033[93mMensaje recibido: \033[0m");
                        switch (mensaje.getTipo()) {
                            case "inicio":
                                System.out.println("Mensaje recibido: " + mensaje.toJson());
                                System.out.println("\033[92mInicio de sesión de " + mensaje.getUsuario() + "\033[0m");
                                System.out.println("\033[92mLista de usuarios activos: \033[0m"+ mensaje.getContenido());
                                if (!mensaje.getContenido().isEmpty()) {
                                    String[] usuariosActivos = mensaje.getContenido().split(",");
                                    for (String usuario : usuariosActivos) {
                                        System.out.println("- " + usuario.trim());
                                    }
                                } else {
                                    System.out.println("No hay usuarios activos.");
                                }
                                break;
                            case "mensaje":
                                System.out.println("\033[93mMensaje de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                                break;
                            case "privado":
                                System.out.println("\033[93mMensaje privado de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
                                break;
                            case "salir":
                                System.out.println("\033[94m" + mensaje.getUsuario() + " salió del servidor\033[0m");
                                break;
                            default:
                                break;
                        }
                        }else {
                            // Si no es JSON, asumimos que son datos binarios (como un fragmento de archivo)
                            System.out.println("Fragmento recibido: " + recv.getLength() + " bytes.");
                            // Procesar fragmento del archivo
                        }

                        // System.out.println("Mensaje recibido: " + mensaje.getTipo() + " de " + mensaje.getUsuario());
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            });

            recibirMensajes.start();

            // Menú para enviar mensajes
            Scanner scanner = new Scanner(System.in);
            boolean continuar = true;

            while (continuar) {
                System.out.println("\n\033[96mMenu");
                System.out.println("1. Enviar mensaje grupal");
                System.out.println("2. Salir del servidor");
                System.out.println("3. Enviar mensaje privado");
                System.out.println("4. Enviar un archivo");
                System.out.println("5. Ver archivos grupales");
                System.out.println("Seleccione una opcion: \033[0m");
                String opcion = scanner.nextLine().trim();

                switch (opcion) {
                    case "1":   // Enviar mensaje grupal
                        System.out.println("Escribe tu mensaje grupal: ");
                        String contenidoMensaje = scanner.nextLine();

                        Mensaje mensaje = new Mensaje("mensaje", nombreUsuario, contenidoMensaje, null);
                        String jsonMensaje = mensaje.toJson();
                        byte[] mensajeBytes = jsonMensaje.getBytes(StandardCharsets.UTF_8);

                        if (mensajeBytes.length > Cliente.dgram_buf_len) {
                            System.err.println("El mensaje es muy largo para enviarlo");
                            continue;
                        }

                        DatagramPacket paqueteMensaje = new DatagramPacket(mensajeBytes, mensajeBytes.length, grupo, puertoMulticast);
                        socket.send(paqueteMensaje);
                        break;

                    case "2":   // Salir del grupo
                        System.out.println("\033[94mSaliendo del servidor...\033[0m");
                        Mensaje salir = new Mensaje("salir", nombreUsuario, "", null);
                        String jsonSalir = salir.toJson();
                        DatagramPacket paqueteSalida = new DatagramPacket(jsonSalir.getBytes(StandardCharsets.UTF_8), jsonSalir.length(), grupo, puertoMulticast);
                        socket.send(paqueteSalida);
                        continuar = false;
                        break;

                    case "3":   // Enviar mensaje privado -- todavía no queda
                        System.out.println("¿A quién deseas mandarle un mensaje privado?");
                        String destinatario = scanner.nextLine().trim();
                        System.out.println("Escribe tu mensaje:");
                        String contenido = scanner.nextLine().trim();

                        Mensaje mensajePrivado = new Mensaje("privado", nombreUsuario, contenido, destinatario);
                        String jsonMensajePrivado = mensajePrivado.toJson();
                        byte[] mensajePrivadoBytes = jsonMensajePrivado.getBytes(StandardCharsets.UTF_8);

                        if (mensajePrivadoBytes.length > Cliente.dgram_buf_len) {
                            System.err.println("El mensaje es muy largo para enviarlo");
                            continue;
                        }

                        DatagramPacket paqueteMensajePrivado = new DatagramPacket(mensajePrivadoBytes, mensajePrivadoBytes.length, grupo, puertoMulticast);
                        socket.send(paqueteMensajePrivado);
                        break;

                    case "4":   // Enviar archivo
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
                                socket.send(paqueteMetadatos);

                                for (int i = 0; i < totalFragments; i++) {
                                    int start = i * fragmentSize;
                                    int end = Math.min(fileBytes.length, start + fragmentSize);
                                    byte[] fragment = Arrays.copyOfRange(fileBytes, start, end);

                                    DatagramPacket paqueteFragmento = new DatagramPacket(fragment, fragment.length, grupo, puertoMulticast);
                                    socket.send(paqueteFragmento);
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

                    case "5":   // Ver y descargar archivos recibidos
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

                    default:
                        break;
                }
            }

            recibirMensajes.interrupt();
            socket.leaveGroup(grupo);
            socket.close();
            scanner.close();
            System.out.println("\033[92mConexión cerrada\033[0m");
            
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce tu nombre de usuario: ");
        String nombreUsuario = scanner.nextLine().trim();
        // new Cliente(nombreUsuario, ).start();;
        new Cliente(nombreUsuario).start();
        // scanner.close();
    }
}
