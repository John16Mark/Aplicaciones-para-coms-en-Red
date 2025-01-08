import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;

public class Servidor extends Thread {
  public static String direccionMulticast = "230.1.1.1";
  public static int puertoMulticast = 4000;
  public static int dgram_buf_len = 1024;
  public ArrayList<String> usuariosActivos;

  private Map<String, InetSocketAddress> usuariosSockets = new HashMap<>();
  private static Map<Integer, byte[]> bufferRecepcion = new HashMap<>();
  
  public static void main(String[] args) {
    try {
      Servidor server = new Servidor();
      server.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void run () {
    usuariosActivos = new ArrayList<>();
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

      // Recibir mensajes
      for (;;) {
        byte[] buf = new byte[dgram_buf_len];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        
        socket.receive(recv);
        String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);

        if (!jsonMensaje.endsWith("}")) {
          System.err.println("\033[91mMensaje JSON truncado: " + jsonMensaje + "\033[0m");
          continue;
        }

        System.out.println("\033[93mDatos recibidos: \033[0m" + jsonMensaje);
        Mensaje mensaje = new Gson().fromJson(jsonMensaje, Mensaje.class);
        
        switch (mensaje.getTipo()) {
          /* ---------------------------------------------------------------------------------------
          *                                  INICIO DE SESIÓN
          * --------------------------------------------------------------------------------------- */
          case "inicioSesion": 
            usuariosActivos.add(mensaje.getUsuario());
            // Se guarda el puerto unicast para mensajes privados
            guardarPuerto(mensaje, recv.getAddress());
            InetSocketAddress direccion = usuariosSockets.get(mensaje.getUsuario());
            int puertoAsignado = direccion.getPort();
            System.out.println("Usuario conectado: " + mensaje.getUsuario() + " desde el puerto " + puertoAsignado + " y direccion " + direccion.getAddress());
            // Se envía lista de usuarios activos actualizado
            String listaUsuariosInicio = new Mensaje("usuariosActivosInicio", mensaje.getUsuario(), String.join(", ", usuariosActivos), null).toJson();
            enviarListaUsuarios(listaUsuariosInicio, socket, grupo);
            break;

          /* ---------------------------------------------------------------------------------------
          *                                  CIERRE DE SESIÓN
          * --------------------------------------------------------------------------------------- */
          case "cierreSesion":
            usuariosActivos.remove(mensaje.getUsuario());
            System.out.println("\033[94mUsuario " + mensaje.getUsuario() + " salió del servidor grupal\033[0m");
  
            if(usuariosActivos.isEmpty()) {
              System.out.println("\033[94mNo hay usuarios conectados\033[0m");
            }else{
              String listaUsuariosCierre = new Mensaje("usuariosActivosCierre", mensaje.getUsuario(), String.join(", ", usuariosActivos), null).toJson();
              enviarListaUsuarios(listaUsuariosCierre, socket, grupo);
            }
            break;

          /* ---------------------------------------------------------------------------------------
          *                                  MENSAJE GRUPAL
          * --------------------------------------------------------------------------------------- */
          case "mensajeGrupal": 
            System.out.println("\033[95mMensaje de " + mensaje.getUsuario() + ":\033[0m " + mensaje.getContenido());
            break;
          
          /* ---------------------------------------------------------------------------------------
          *                    MENSAJES PRIVADOS - ENVÍA DIRECCIÓN DESTINATARIO
          * --------------------------------------------------------------------------------------- */
          case "direccionDestinatario":
            // Obtener dirección del usuario solicitado
            String usuarioDestino = mensaje.getDestinatario(); 
            InetSocketAddress direccionDestino = usuariosSockets.get(usuarioDestino);
            System.out.println("\033[96mPuerto destino\033[0m " + direccionDestino.getPort() + " \033[96mdirección\033[0m " + direccionDestino.getAddress());
            // Obtener dirección del usuario solicitante
            String usuarioOrigen = mensaje.getUsuario(); 
            InetSocketAddress direccionOrigen = usuariosSockets.get(usuarioOrigen);
            System.out.println("\033[96mPuerto origen\033[0m " + direccionOrigen.getPort() + " \\033[96mdirección\\033[0m " + direccionOrigen.getAddress());

            String direccionPuerto = direccionDestino.getAddress() + ":" + direccionDestino.getPort();
            String mensajeConfirmacion = new Mensaje("mensajeConfirmacion", mensaje.getUsuario(), mensaje.getContenido(), direccionPuerto).toJson();
            DatagramPacket paqueteConfirmacion = new DatagramPacket(mensajeConfirmacion.getBytes(StandardCharsets.UTF_8), mensajeConfirmacion.length(), direccionOrigen.getAddress(), direccionOrigen.getPort());
            socket.send(paqueteConfirmacion);
            break;

          /* ---------------------------------------------------------------------------------------
          *                                  ARCHIVOS - CAMBIARLOS
          * --------------------------------------------------------------------------------------- */
          case "archivo":
            String nombreArchivo = mensaje.getContenido();
            long totalFragments = Long.parseLong(mensaje.getDestinatario());
            System.out.println("Recibiendo archivo: " + nombreArchivo + " en " + totalFragments + " fragmentos");
        
            for (String usuarioActivo : usuariosActivos) {
              if (!usuarioActivo.equals(mensaje.getUsuario())) {
                System.out.println("\nRecibiendo archivo: " + nombreArchivo + " (" + totalFragments + " bytes) de " + mensaje.getUsuario());

                File userDir = new File(usuarioActivo);
                if (!userDir.exists()) {
                  if (userDir.mkdir()) {
                    System.out.println("Carpeta creada para el usuario " + usuarioActivo);
                  }else {
                    System.out.println("No se pudo crear la carpeta para el usuario: " + usuarioActivo);
                  }
                }

                File guardarArchivo = new File(userDir, nombreArchivo);

                try (FileOutputStream fos = new FileOutputStream(guardarArchivo)) {
                  long bytesRecibidos = 0;
                  long startTime = System.currentTimeMillis();
                  long timeoutLimit = 10000;

                  while (bytesRecibidos < totalFragments) {
                      try {
                          byte[] data = new byte[1028];
                          DatagramPacket receivedPacket = new DatagramPacket(data, data.length);
                          socket.receive(receivedPacket);

                          if (receivedPacket.getLength() >= 4) {
                              ByteArrayInputStream bais = new ByteArrayInputStream(receivedPacket.getData());
                              DataInputStream in = new DataInputStream(bais);
                              int sequenceNumber = in.readInt();
                              int bytesRead = receivedPacket.getLength();

                              if (sequenceNumber >= 0) {
                                  if (!bufferRecepcion.containsKey(sequenceNumber)) {
                                      byte[] fileData = Arrays.copyOfRange(data, 4, bytesRead);
                                      bufferRecepcion.put(sequenceNumber, fileData);
                                      fos.write(fileData);
                                      bytesRecibidos += (bytesRead - 4);
                                  }
                              }
                          }
                      } catch (SocketTimeoutException e) {
                          if (System.currentTimeMillis() - startTime > timeoutLimit) {
                              System.out.println("Tiempo de espera agotado");
                              break;
                          }
                      }
                  }

                  if (bytesRecibidos >= totalFragments) {
                    System.out.println("Archivo recibido completamente");
                  } else {
                      System.out.println("Transferencia incompleta: " + bytesRecibidos + "/" + totalFragments + " bytes");
                  }

                }
              }
            }
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

  /* ------------------------------------------------------------------------------------------------------
  *                       
  *                                      MANDAR LISTA DE USUARIOS ACTUALIZADA
  * 
  * ------------------------------------------------------------------------------------------------------  */
  private void enviarListaUsuarios(String listaUsuarios, MulticastSocket socket, InetAddress grupo) throws IOException {
    DatagramPacket paqueteUsuarios = new DatagramPacket(listaUsuarios.getBytes(StandardCharsets.UTF_8), listaUsuarios.length(), grupo, puertoMulticast);
    socket.send(paqueteUsuarios);
    System.out.println("\033[93mEnviando lista de usuarios: \033[0m" + listaUsuarios);  
  }

  /* ------------------------------------------------------------------------------------------------------
  *                       
  *                                         GUARDAR PUERTO DE USUARIOS
  * 
  * ------------------------------------------------------------------------------------------------------  */
  private void guardarPuerto(Mensaje mensaje, InetAddress dir) {
    int puerto = Integer.parseInt(mensaje.getContenido());
    InetSocketAddress direccion = new InetSocketAddress(dir, puerto);
    usuariosSockets.put(mensaje.getUsuario(), direccion);
    System.out.println("\033[96mPuerto del usuario\033[0m " + mensaje.getUsuario() + "\033[96m:\033[0m " + direccion.getPort() + " \033[96mcon direccion\033[0m " + direccion.getAddress());
  }
}