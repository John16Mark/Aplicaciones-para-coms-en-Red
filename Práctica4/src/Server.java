import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

class Server {
    public static final int PUERTO = 8000;
    ServerSocket serverSocket;

    class Manejador extends Thread {
        protected Socket socket;
        DataOutputStream outStream;
        DataInputStream inStream;

        protected String nombreArchivo;
        StringBuilder cabeceras;
        StringBuilder contenido;
        Map<String, String> parametros;

        String directorio = "data";
        String sep = "\033[0m------------------------------------";

        public Manejador(Socket _socket) throws Exception {
            this.socket = _socket;
        }

        public void run() {
            try {
                outStream = new DataOutputStream(socket.getOutputStream());
                inStream = new DataInputStream(socket.getInputStream());
                System.err.println();
                byte[] buffer = new byte[50000];
                int size = inStream.read(buffer);
                if (size == -1) {
                    System.out.println("\033[91m\nConexión cerrada por el cliente o sin datos recibidos.\033[0m");
                    socket.close();
                    return;
                }

                // Obtener toda la petición HTTP
                String peticion = new String(buffer, 0, size);
                System.out.println(sep+"\033[92m\nCliente conectado\033[0m");
                System.out.println("\033[93mIP:\033[0m "+socket.getInetAddress());
                System.out.println("\033[93mPuerto:\033[0m "+socket.getPort());
                System.out.println("\033[93mSolicitud:\033[0m\n"+peticion);

                // Obtener la primera línea y el método
                StringTokenizer stringTokenizer = new StringTokenizer(peticion, "\n");
                String primeraLinea = stringTokenizer.nextToken();
                String metodo = primeraLinea.split(" ")[0].toUpperCase();

                // Separar las cabeceras y el contenido.
                cabeceras = new StringBuilder();
                contenido = new StringBuilder();
                boolean esContenido = false;
                while(stringTokenizer.hasMoreTokens()) {
                    String linea = stringTokenizer.nextToken();
                    if(linea.isEmpty() || linea.equals("\r")) {
                        esContenido = true;
                        continue;
                    }
                    if(esContenido)
                        contenido.append(linea + "\n");
                    else
                        cabeceras.append(linea + "\n");
                }

                // Obtener parámetros.
                String[] primeraLineaPartes = primeraLinea.split(" ");
                String uri = primeraLineaPartes[1];
                parametros = new HashMap<>();
                if (uri.contains("?")) {
                    String[] uriPartes = uri.split("\\?", 2);
                    uri = uriPartes[0];
                    String queryString = uriPartes[1];

                    // Separar los parámetros del nombre
                    String[] pares = queryString.split("&");
                    for (String par : pares) {
                        String[] keyValue = par.split("=", 2);
                        String clave = URLDecoder.decode(keyValue[0], "UTF-8");
                        String valor = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
                        parametros.put(clave, valor);
                    }
                }

                System.out.print("\033[93mCabeceras:\033[0m\n"+cabeceras);
                System.out.print("\033[93mContenido:\033[0m\n"+contenido);
                System.out.print("\033[93mParámetros:\033[0m\n"+parametros+"\n");

                switch(metodo) {
                    case "GET":
                        System.out.println("\033[94m\nMétodo GET\033[0m");
                        GET(primeraLinea);
                        break;
                    case "POST":
                        System.out.println("\033[94m\nMétodo POST\033[0m");
                        POST(stringTokenizer);
                        break;
                    case "PUT":
                        System.out.println("\033[94m\nMétodo PUT\033[0m");
                        PUT(primeraLinea, contenido, parametros);
                        break;
                    case "DELETE":
                        System.out.println("\033[94m\nMétodo DELETE\033[0m");
                        DELETE(primeraLinea, stringTokenizer);
                        break;
                    case "HEAD":
                        System.out.println("\033[94m\nMétodo HEAD\033[0m");
                        HEAD();
                        break;
                    default:
                        enviarError("501 Not Implemented", "Método no manejado");
                        outStream.flush();
                        outStream.close();
                        socket.close();
                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void GET(String line) throws Exception{
            if(line.indexOf("?") == -1) {
                getArchivo(line);
                if(nombreArchivo.compareTo("") == 0) {
                    sendArchivo("index.html");
                } else {
                    System.out.println("\033[92mNombre del archivo:\033[0m " + nombreArchivo);
                    sendArchivo("./data/"+nombreArchivo);
                }
            } else if (line.toUpperCase().startsWith("GET")) {
                System.out.println("\033[93mParámetros:\033[0m " + parametros);

                // Formato de parámetros
                String parametrosString = "";
                for (Map.Entry<String, String> entry : parametros.entrySet()) {
                    parametrosString += "<b>" + entry.getKey() + "</b> = " + entry.getValue() + "<br>\n";
                }

                // Generar la respuesta.
                StringBuffer respuesta = new StringBuffer();
                respuesta.append("HTTP/1.0 200 OK \n");
                String fecha = "Date: " + new Date()+" \n";
                respuesta.append(fecha);
                String tipo_mime = "Content-Type: text/html \n\n";
                respuesta.append(tipo_mime);
                respuesta.append("<html><head><title>SERVIDOR WEB</title></head>\n");
                respuesta.append("<body style=\"background-color: #ffffff; font-family:'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\"> " +
                "<div style=\"width: 100%; align-self: center; text-align: center;\"><h1>Parametros Obtenidos..</h1>\n");
                respuesta.append("<div style=\"\r\n" + //
                                "width: 60%;\r\n" + //
                                "margin: 0 auto;\r\n" + //
                                "padding: 10px;\r\n" + //
                                "position: relative;\r\n" + //
                                "background-color: #2589e6;\" >" + parametrosString + "");
                respuesta.append("</div></body></html>\n\n");
                System.out.println("\033[93m\nRespuesta:");
                System.out.print("\033[32m"+respuesta);
                System.out.println(sep);
                outStream.write(respuesta.toString().getBytes());
                outStream.flush();
                outStream.close();
                socket.close();
            }
        }

        public void POST(StringTokenizer stringTokenizer) throws Exception {
            StringBuilder contenido = new StringBuilder();
            while(stringTokenizer.hasMoreTokens())
                contenido.append(stringTokenizer.nextToken()).append("\n");
            System.out.println("\033[96mContenido:\033[0m "+contenido);
            String respuesta = "HTTP/1.0 200 OK\nContent-Type: text/plain\n\nDatos recibidos correctamente.\n";
            System.out.println("\033[93m\nRespuesta:");
            System.out.print("\033[31m"+respuesta);
            System.out.println(sep);
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void PUT(String primeraLinea, StringBuilder contenido, Map<String,String> parametros) throws Exception {
            File dir = new File(directorio);
            if(!dir.exists())
                dir.mkdirs();
            
            // Obtener el nombre del archivo
            String[] tokens = primeraLinea.split(" ");
            String uri = tokens[1];
            uri = uri.substring(1);
            String[] uriPartes = uri.split("\\?", 2);
            String nombreArchivo = uriPartes[0];
            System.out.println("\033[92mNombre del archivo:\033[0m " + nombreArchivo);
            if(nombreArchivo.isEmpty()) {
                enviarError("400 Bad Request", "Nombre de archivo no especificado");
                return;
            }

            // Crear el archivo
            System.out.println("\033[92mContenido:\033[0m "+contenido);
            if(!parametros.isEmpty()) {
                enviarError("400 Bad Request", "No se admiten argumentos en la solicitud PUT.");
                return;
            }
            File archivo = new File(directorio + File.separator + nombreArchivo);
            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                fos.write(contenido.toString().getBytes());
                fos.flush();
            }
            System.out.println("\033[96m\nArchivo guardado:\033[0m\n" + archivo.getAbsolutePath());

            // Respuesta
            String respuesta = "HTTP/1.0 200 OK\nContent-Type: text/plain\n\nRecurso creado o actualizado correctamente.\n";
            System.out.println("\033[93m\nRespuesta:");
            System.out.print("\033[32m"+respuesta+"\033[0m");
            System.out.println(sep);
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void DELETE(String primeraLinea, StringTokenizer stringTokenizer) throws Exception {
            File dir = new File(directorio);
            if(!dir.exists())
                dir.mkdirs();
            
            // Obtener el nombre del archivo
            String[] tokens = primeraLinea.split(" ");
            String uri = tokens[1];
            uri = uri.substring(1);
            String[] uriPartes = uri.split("\\?", 2);
            String nombreArchivo = uriPartes[0];
            System.out.println("\033[92mNombre del archivo:\033[0m " + nombreArchivo);
            if (nombreArchivo.isEmpty()) {
                enviarError("400 Bad Request", "Nombre de archivo no especificado.");
                return;
            }
            File archivo = new File(directorio + File.separator + nombreArchivo);

            // Si hay parámetros dar error
            if(uriPartes.length != 1) {
                enviarError("400 Bad Request", "No se admiten argumentos en la solicitud DELETE.");
                return;
            }

            // Eliminar archivo
            if (archivo.exists()) {
                if (archivo.delete()) {
                    System.out.println("\033[96mArchivo eliminado: " + archivo.getAbsolutePath() + "\033[0m\n");
                    String respuesta = "HTTP/1.0 200 OK\nContent-Type: text/plain\n\nArchivo eliminado: " + nombreArchivo + "\n";
                    System.out.println("\033[93m\nRespuesta:");
                    System.out.print("\033[32m"+respuesta+"\033[0m");
                    System.out.println(sep);
                    outStream.write(respuesta.getBytes());
                } else {
                    enviarError("500 Internal Server Error", "No se pudo eliminar el archivo.");
                }
            } else {
                enviarError("404 Not Found", "El archivo no existe.");
            }

            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void HEAD() throws Exception {
            String respuesta = "HTTP/1.0 200 OK\n" +
                       "Content-Type: text/html\n" +
                       "Content-Length: 0\n\n";
            System.out.println("\033[93m\nRespuesta:");
            System.out.print("\033[32m"+respuesta+"\033[0m");
            System.out.println(sep);
            outStream.write(respuesta.getBytes());
            outStream.flush();
        }
/*
        public void enviarSuccess(String codigo, String mensaje) throws Exception{
            String respuesta = "HTTP/1.0 "+ codigo +"\n" +
                "Content-Type: text/plain\n\n" + mensaje + "\n";
            System.out.println("\033[93m\nRespuesta:");
            System.out.print("\033[32m"+respuesta+"\033[0m");
            System.out.println(sep);
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }*/

        public void enviarError(String codigo, String mensaje) throws Exception{
            String respuesta = "HTTP/1.0 "+ codigo +"\n" +
                "Content-Type: text/plain\n\n" + mensaje + "\n";
            System.out.println("\033[93m\nRespuesta:");
            System.out.print("\033[31m"+respuesta);
            System.out.println(sep);
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void getArchivo(String line) {
            int i;
            int fileIndex;
            if(line.toUpperCase().startsWith("GET")) {
                i=line.indexOf("/");
                fileIndex=line.indexOf(" ", i);
                nombreArchivo = line.substring(i+1, fileIndex);
            }
        }

        public void sendArchivo(String nombre) {
            try {
                File file = new File(nombre);
                if(!file.exists()) {
                    enviarError("404 Not Found", "El archivo no existe.");
                    return;
                }

                // Obtener el tipo MIME del archivo
                String mimeType = java.nio.file.Files.probeContentType(file.toPath());
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                FileInputStream archivoStream = new FileInputStream(nombre);
                DataInputStream inTemp = new DataInputStream(archivoStream);
                byte[] buffer = new byte[1024];
                int x = 0;
                long size = file.length();
                long cont = 0;

                // Crear la respuesta del servidor
                String sb = "";
                sb = sb + "HTTP/1.0 200 OK\n";
                sb = sb + "Server: Juan y Paola Server/1.0\n";
                sb = sb + "Date: " + new Date()+" \n";
                sb = sb + "Content-Type: " + mimeType + " \n";
                sb = sb + "Content-Length: "+size+" \n";
                sb = sb + "\n";
                System.out.println("\033[93m\nRespuesta:");
                System.out.print("\033[32m"+sb+"\033[0m");
                outStream.write(sb.getBytes());
                outStream.flush();

                // Escribir el contenido del archivo
                while(cont < size) {
                    x = inTemp.read(buffer);
                    outStream.write(buffer,0, x);
                    cont = cont+x;
                    outStream.flush();
                }
                System.out.println(sep);
                inTemp.close();
                outStream.close();

            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }
        /*
        private String getMimeManual(File file) {
            String extension = "";
            String fileName = file.getName();
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }
        
            // Mapa de extensiones a tipos MIME
            switch (extension) {
                case "html": return "text/html";
                case "htm": return "text/html";
                case "txt": return "text/plain";
                case "css": return "text/css";
                case "js": return "application/javascript";
                case "json": return "application/json";
                case "xml": return "application/xml";
                case "jpg": return "image/jpeg";
                case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "gif": return "image/gif";
                case "svg": return "image/svg+xml";
                case "ico": return "image/x-icon";
                case "pdf": return "application/pdf";
                case "zip": return "application/zip";
                case "rar": return "application/x-rar-compressed";
                default: return null; // Tipo desconocido
            }
        }
        */
    }

    public Server() throws Exception {
        System.out.println("\033[95mIniciando Servidor...");
        this.serverSocket = new ServerSocket(PUERTO);
        System.out.println("Servidor iniciado.");
        System.out.println("Esperando clientes...\033[0m");

        while(true) {
            Socket socket = serverSocket.accept();
            new Manejador(socket).start();
        }
    }

    public static void main(String[] args) throws Exception{
        @SuppressWarnings("unused")
        Server server = new Server();
    }
}