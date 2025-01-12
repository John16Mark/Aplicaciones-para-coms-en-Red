import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import com.google.gson.Gson;

public class Servidor extends Thread {
  public final static String direccionMulticast = "230.1.1.1";
  public final static int puertoMulticast = 4000;
  public final static int puertoUnicast = 4001;
  public static int dgram_buf_len = 1024;
  public static ArrayList<String> usuariosActivos;

  final public static int TAM_BUFFER = 65535;

  private static Map<String, InetSocketAddress> usuariosSockets = new HashMap<>();
  private static Map<String, InetSocketAddress> usuariosPuertosArchivos = new HashMap<>();
  private static Map<Integer, byte[]> bufferRecepcion = new HashMap<>();

  private InetSocketAddress solicitanteArchivo;
  private String solicitanteStringArchivo;
  
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
    final InetAddress grupo;

    try {
      grupo = InetAddress.getByName(direccionMulticast);
    }catch (UnknownHostException e) {
      e.printStackTrace();
      System.exit(1);
      throw new RuntimeException("Error al obtener dirección multicast");
    }

    try (MulticastSocket socket = new MulticastSocket(puertoMulticast);
        DatagramSocket socketUnicast = new DatagramSocket(puertoUnicast)) {
        // Servidor multicast
        socket.joinGroup(grupo);

        System.out.println("\033[92mServidor iniciado y unido al grupo multicast" + puertoMulticast + ".\033[0m");
        System.out.println("\033[92mServidor con puerto único " + puertoUnicast + " para recepción de archivos.\033[0m");

        Thread recibirMensajesMulticast = new Thread(() -> {
          for (;;) {
            try {
              byte[] buf = new byte[dgram_buf_len];
              DatagramPacket recv = new DatagramPacket(buf, buf.length);
              socket.receive(recv);
              String jsonMensaje = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
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
                  // Se guarda puerto unicast para envío de archivos
                  guardarPuertoArchivos(mensaje, recv.getAddress());
                  InetSocketAddress direccion = usuariosSockets.get(mensaje.getUsuario());
                  int puertoAsignado = direccion.getPort();
                  System.out.println("\033[96mUsuario conectado:\033[0m " + mensaje.getUsuario() + " desde el puerto " + puertoAsignado + " y direccion " + direccion.getAddress());
                  InetSocketAddress direccionArchivos = usuariosPuertosArchivos.get(mensaje.getUsuario());
                  int puertoArchivos = direccionArchivos.getPort();
                  System.out.println("\033[96mUsuario conectado:\033[0m " + mensaje.getUsuario() + " desde el puerto para archivos " + puertoArchivos + " y direccion " + direccionArchivos.getAddress());
                  // Se envía lista de usuarios activos actualizado y puerto unicast del servidor
                  final String listaUsuariosInicio = new Mensaje("usuariosActivosInicio", mensaje.getUsuario(), String.join(", ", usuariosActivos), Integer.toString(puertoUnicast)).toJson();
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
                    final String listaUsuariosCierre = new Mensaje("usuariosActivosCierre", mensaje.getUsuario(), String.join(", ", usuariosActivos), null).toJson();
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

                  String mensajeConfirmacion = new Mensaje("mensajeConfirmacion", mensaje.getUsuario(), null, mensaje.getDestinatario()).toJson();
                  DatagramPacket paqueteConfirmacion = new DatagramPacket(mensajeConfirmacion.getBytes(StandardCharsets.UTF_8), mensajeConfirmacion.length(), direccionOrigen.getAddress(), direccionOrigen.getPort());
                  socket.send(paqueteConfirmacion);
                  // Le envía el mensaje privado al destinatario
                  String mensajeDestinatario = new Mensaje("mensajePrivado", mensaje.getUsuario(), mensaje.getContenido(), null).toJson();
                  byte[] mensajeBytes = mensajeDestinatario.getBytes(StandardCharsets.UTF_8);
                  DatagramPacket paqueteDestinatario = new DatagramPacket(mensajeBytes, mensajeBytes.length, direccionDestino.getAddress(), direccionDestino.getPort());
                  socket.send(paqueteDestinatario);
                  break;
                
                case "solicitudEnviarArchivo":
                  String usuario = mensaje.getUsuario();
                  solicitanteStringArchivo = mensaje.getUsuario();
                  solicitanteArchivo = usuariosPuertosArchivos.get(usuario);
                  System.out.println("\033[96mPuerto origen\033[0m " + solicitanteArchivo.getPort() + " \\033[96mdirección\\033[0m " + solicitanteArchivo.getAddress());
                  break;

                default:
                  break;
              }
            }catch (IOException e) {
              e.printStackTrace();
            }
          }
        });

        Thread recibirMensajesUnicast = new Thread(() -> {
          // Clases para enviar información
          ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
          DataOutputStream outStream = new DataOutputStream(byteOut);
          // Clases para recibir información
          ByteArrayInputStream byteIn;
          DataInputStream inStream;
          // Información del archivo
          int totalPackets = -1;
          int expectedPacket = 0;
          String nombreArchivo = "";

          File dir = new File("ArchivosServidor");
          if (!dir.exists())
            dir.mkdirs();

          for(;;) {
            try {
              // Recibir ACK
              byte[] buffer = new byte[TAM_BUFFER];
              DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
              socketUnicast.receive(packet);
              byteIn = new ByteArrayInputStream(packet.getData());
              inStream = new DataInputStream(byteIn);
              
              int numPaquete = inStream.readInt();      // Numero de paquete
              if(totalPackets == -1) {
                totalPackets = inStream.readInt();      // Total de paquetes
              }else {
                inStream.readInt();
              }
              int tamFileName = inStream.readInt();     // Tamaño de la ruta del archivo
              byte[] bufferIn = new byte[tamFileName];  // Ruta archivo en bytes
              inStream.read(bufferIn);
              if (nombreArchivo == "")
                nombreArchivo = new String(bufferIn);   // Cadena de los bytes
              int tam = inStream.readInt();             // Tamaño de los datos
              bufferIn = new byte[tam];
              inStream.read(bufferIn);                  // Datos en bytes
              /* ------------------------------------------------- */
              // Guardar el archivo en la carpeta del servidor
              File outputFileServidor = new File(dir, nombreArchivo);
              try (FileOutputStream fileOut = new FileOutputStream(outputFileServidor, true)) {
                  fileOut.write(bufferIn);
              }catch(IOException e) {
                System.err.println("Error al escribir en el archivo: " + e.getMessage());
                System.err.flush();
              }
              // Guardar el archivo en la carpeta de todos los usuarios activos
              for (String usuario : usuariosActivos) {
                File dirUsuario = new File("ArchivosUsuarios/", usuario); // Carpeta del usuario
                if (!dirUsuario.exists()) 
                    dirUsuario.mkdirs();

                File outputFileUsuario = new File(dirUsuario, nombreArchivo); // Archivo en la carpeta del usuario
                try (FileOutputStream fileOut = new FileOutputStream(outputFileUsuario, true)) {
                    fileOut.write(bufferIn);
                }catch(IOException e) {
                  System.err.println("Error al escribir en el archivo del usuario " + usuario + ": " + e.getMessage());
                  System.err.flush();
                }
              }
              // Enviar ACK solo si el archivo se guardó correctamente
              if (expectedPacket == numPaquete) {
                outStream.writeInt(numPaquete);
                outStream.flush();
                byte[] bufferOut = byteOut.toByteArray();
                DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, packet.getAddress(), packet.getPort());
                socket.send(ACK);
                byteOut.reset();
                expectedPacket++;
              }
              /* ------------------------------------------------- */
              // File outputFile = new File(dir, nombreArchivo);
              // try (FileOutputStream fileOut = new FileOutputStream(outputFile, true)) {
              //   fileOut.write(bufferIn);
              //   // Está en el try para que no mande el acuse si es que no se escribió bien el archivo
              //   if(expectedPacket == numPaquete) {
              //     outStream.writeInt(numPaquete);
              //     outStream.flush();
              //     byte[] bufferOut = byteOut.toByteArray();
              //     DatagramPacket ACK = new DatagramPacket(bufferOut, bufferOut.length, solicitanteArchivo.getAddress(), solicitanteArchivo.getPort());
              //     socket.send(ACK);
              //     byteOut.reset();
              //     expectedPacket++;
              //   }
              // }catch(IOException e) {
              //   System.err.println("Error al escribir en el archivo: " + e.getMessage());
              //   System.err.flush();
              // }

              System.out.println("\033[92mPaquete recibido. \033[95m#paq: \033[0m"+numPaquete+ "\t\033[95mTotalPaq: \033[0m"+totalPackets+"\t\033[95mFileName: \033[0m"+nombreArchivo+"\t\033[95mtam: \033[0m"+tam+" bytes");
              System.out.flush();
              inStream.close();

              if(expectedPacket == totalPackets) {
                System.out.println("\033[94mRecibo exitoso del archivo "+nombreArchivo+".\033[0m");
                System.out.flush();
                try {
                  Mensaje mensajeArchivo = new Mensaje("mensajeArchivo", solicitanteStringArchivo, null, null);
                  String jsonArchivo = mensajeArchivo.toJson();
                  DatagramPacket paqueteArchivo = new DatagramPacket(jsonArchivo.getBytes(StandardCharsets.UTF_8), jsonArchivo.length(), grupo, puertoMulticast);
                  socket.send(paqueteArchivo);
                }catch (Exception e) {
                  System.err.println("\033[91mEl Mensaje de archivo exitoso no se mandó correctamente:\033[0m " + e.getMessage());
                }
                expectedPacket = 0;
                //handshake = false;
                totalPackets = -1;
                nombreArchivo = "";
              }
              
            }catch(IOException e) {
              // System.out.println("\033[31mTIMEOUT: no se recibió el paquete esperado.\033[0m");
              System.out.flush();
              expectedPacket = 0;
              totalPackets = -1;
              nombreArchivo = "";
            }
          }
        });

        recibirMensajesMulticast.start();
        recibirMensajesUnicast.start();

        Scanner scanner = new Scanner(System.in);
        boolean continuar = true;

        while (continuar) {
          String opcion = scanner.nextLine().trim();
          switch(opcion) {
            case "1":
              continuar = false;
              break;
            default:
              break;
          }
        }

        recibirMensajesMulticast.interrupt();
        recibirMensajesUnicast.interrupt();
        socket.leaveGroup(grupo);
        socket.close();
        socketUnicast.close();

        scanner.close();
        System.exit(0);
    }catch (IOException e) {
      e.printStackTrace();
    }
  }

  /* ------------------------------------------------------------------------------------------------------
  *                       
  *                                      MANDAR LISTA DE USUARIOS ACTUALIZADA
  * 
  * ------------------------------------------------------------------------------------------------------  */
  private void enviarListaUsuarios(final String listaUsuarios, MulticastSocket socket, InetAddress grupo) throws IOException {
    DatagramPacket paqueteUsuarios = new DatagramPacket(listaUsuarios.getBytes(StandardCharsets.UTF_8), listaUsuarios.length(), grupo, puertoMulticast);
    socket.send(paqueteUsuarios);
    System.out.println("\033[93mEnviando lista de usuarios: \033[0m" + listaUsuarios);  
  }

  /* ------------------------------------------------------------------------------------------------------
  *                       
  *                               GUARDAR PUERTO DE USUARIOS PARA MENSAJES PRIVADOS
  * 
  * ------------------------------------------------------------------------------------------------------  */
  private void guardarPuerto(Mensaje mensaje, InetAddress dir) {
    int puerto = Integer.parseInt(mensaje.getContenido());
    InetSocketAddress direccion = new InetSocketAddress(dir, puerto);
    usuariosSockets.put(mensaje.getUsuario(), direccion);
    System.out.println("\033[96mPuerto del usuario\033[0m " + mensaje.getUsuario() + "\033[96m:\033[0m " + direccion.getPort() + " \033[96mcon direccion\033[0m " + direccion.getAddress());
  }

  /* ------------------------------------------------------------------------------------------------------
  *                       
  *                               GUARDAR PUERTO DE USUARIOS PARA ENVÍO DE ARCHIVOS
  * 
  * ------------------------------------------------------------------------------------------------------  */
  private void guardarPuertoArchivos(Mensaje mensaje, InetAddress dir) {
    int puerto = Integer.parseInt(mensaje.getDestinatario());
    InetSocketAddress direccion = new InetSocketAddress(dir, puerto);
    usuariosPuertosArchivos.put(mensaje.getUsuario(), direccion);
    System.out.println("\033[96mPuerto del usuario para envío de archivos\033[0m " + mensaje.getUsuario() + "\033[96m:\033[0m " + direccion.getPort() + " \033[96mcon direccion\033[0m " + direccion.getAddress());
  }
}