import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;

public class Servidor extends Thread {
  public static final String direccionMulticast = "230.1.1.1";
  public static final int puertoMulticast = 4000;
  public static final int dgram_buf_len = 1024;
  private ArrayList<String> usuarios;
  private Map<String, InetAddress> usuariosSockets = new HashMap<>();
  private int puertoBase = 5000;

  private void asignarPuerto(String nombreUsuario) {
    int puerto = puertoBase + usuarios.size();
    // InetSocketAddress direccion = new InetSocketAddress("127.0.0.1", puerto);
    usuariosSockets.put(nombreUsuario, null);
    // usuariosSockets.put(nombreUsuario, direccion);
    System.out.println("Puerto asignado a " + nombreUsuario + ": " + puerto);
  }

  private void enviarListaUsuarios(MulticastSocket socket, InetAddress grupo) throws IOException {
    String listaUsuarios = new Mensaje("usuarios", "", String.join(",", usuarios), null).toJson();
    DatagramPacket paqueteUsuarios = new DatagramPacket(listaUsuarios.getBytes(StandardCharsets.UTF_8), listaUsuarios.length(), grupo, puertoMulticast);
    socket.send(paqueteUsuarios);
    System.out.println("Enviando lista de usuarios " + listaUsuarios);
  }

  public void run () {
    usuarios = new ArrayList<>();
    InetAddress grupo = null;

    try {
      grupo = InetAddress.getByName(direccionMulticast);
    }catch (UnknownHostException e) {
      e.printStackTrace();
      System.exit(1);
    }

    // Se inicia el servidor
    try (MulticastSocket socket = new MulticastSocket(puertoMulticast)) {
      socket.joinGroup(grupo);
      System.out.println("\033[92mServidor iniciado y unido al grupo multicast.\033[0m");

      // Recibimos datos de un usuario
      for (;;) {
        byte[] buf = new byte[dgram_buf_len];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        
        socket.receive(recv);
        // System.out.println("Paquete reibido desde " + recv.getAddress() + ": " + recv.getPort());
        // byte[] data = recv.getData();
        // String mensaje = new String(data);
        String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);

        if (!jsonMensaje.endsWith("}")) {
          System.err.println("\033[91mMensaje JSON truncado: " + jsonMensaje + "\033[0m");
          continue;
        }

        System.out.println("\033[93mDatos recibidos: \033[0m" + jsonMensaje);

        Mensaje mensaje = new Gson().fromJson(jsonMensaje, Mensaje.class);
        
        switch (mensaje.getTipo()) {
          case "inicio":  // Se manda lista de usuarios conectados -- no funciona ;-;
            usuarios.add(mensaje.getUsuario());
            // asignarPuerto(mensaje.getUsuario());
            // String direccionIP = recv.getAddress().getHostAddress();
            // System.out.println("direccion " + direccionIP);
            System.out.println("Usuario conectado: " + mensaje.getUsuario());
            enviarListaUsuarios(socket, grupo);
            break;
          
          case "mensaje": // Para mensajes grupales y privados
            System.out.println("\033[95mMensaje de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
            break;

          case "privado":
            String destinatario = mensaje.getDestinatario();
            InetAddress direccionDestinatario = usuariosSockets.get(destinatario);
        
            // Verificar si la dirección del destinatario está en la lista de usuarios conectados
            if (direccionDestinatario != null) {
                // Crear el mensaje privado y enviarlo solo al destinatario
                try (DatagramSocket socketPrivado = new DatagramSocket()) {
                  String mensajePrivadoJson = new Mensaje("privado", mensaje.getUsuario(), mensaje.getContenido(), destinatario).toJson();
                  DatagramPacket paquetePrivado = new DatagramPacket(mensajePrivadoJson.getBytes(StandardCharsets.UTF_8), mensajePrivadoJson.length(), direccionDestinatario, puertoMulticast);
                  socketPrivado.send(paquetePrivado);
                }
                System.out.println("Mensaje privado enviado a: " + destinatario);
            }else {
                System.out.println("El destinatario no está conectado: " + destinatario);
            }
            break;
          
          case "archivo": // Para recibir archivos (se crea una carpeta de recibidos, pero hay que cambiarlo)
            String nombreArchivo = mensaje.getContenido();
            int totalFragments = Integer.parseInt(mensaje.getDestinatario());
            System.out.println("Recibiendo archivo: " + nombreArchivo + " en " + totalFragments + " fragmentos");
        
            // Crear carpeta si no existe
            File carpetaRecibidos = new File("recibidos");
            if (!carpetaRecibidos.exists()) {
                carpetaRecibidos.mkdir();
            }
        
            // Buffer para reconstruir el archivo
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
            try {
                for (int i = 0; i < totalFragments; i++) {
                    byte[] buffer = new byte[10240]; // 10 KB
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paquete);
                    baos.write(paquete.getData(), 0, paquete.getLength());
                    System.out.println("Recibido fragmento " + (i + 1) + " de " + totalFragments);
                }
        
                // Guardar el archivo
                File archivo = new File(carpetaRecibidos, nombreArchivo);
                try (FileOutputStream fos = new FileOutputStream(archivo)) {
                    baos.writeTo(fos);
                }
                System.out.println("Archivo guardado correctamente: " + archivo.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("Error al recibir o guardar el archivo: " + e.getMessage());
            }
            break;

          case "salir":
            usuarios.remove(mensaje.getUsuario());
            System.out.println("\033[94mUsuario " + mensaje.getUsuario() + " salió del servidor grupal\033[0m");
            enviarListaUsuarios(socket, grupo);
            break;
            
          default:
            break;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(2);
    }
  }

  public static void main(String[] args) {
    try {
      Servidor server = new Servidor();
      server.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

// if (mensaje.startsWith("<inicio>")) {
        //   String nombre = mensaje.substring(8).trim();

        //   if (!usuarios.contains(nombre)) {
        //     usuarios.add(nombre);
        //   }

        //   String listaUsuarios = "<usuarios>" + usuarios.toString();
        //   DatagramPacket paquete = new DatagramPacket(listaUsuarios.getBytes(), listaUsuarios.length(), grupo, puertoMulticast);
        //   socket.send(paquete);
        //   System.out.println("Lista actualizada enviada: " + listaUsuarios);
        // }
        // else if (mensaje.startsWith("<mensaje>")) {
        //   System.out.println("Mensaje grupal recibido: "+ mensaje);
        //   DatagramPacket paquete = new DatagramPacket(mensaje.getBytes(), mensaje.length(), grupo, puertoMulticast);
        //   socket.send(paquete);
        // }
        // else if (mensaje.startsWith("<salir>")) {
        //   String nombre = mensaje.substring(7).trim();
        //   usuarios.remove(nombre);
        //   System.out.println("Usuario desconectado: " + nombre);
        //   String listaUsuarios = "<usuarios>" + usuarios.toString();
        //   DatagramPacket paquete = new DatagramPacket(listaUsuarios.getBytes(), listaUsuarios.length(), grupo, puertoMulticast);
        //   socket.send(paquete);
        // }