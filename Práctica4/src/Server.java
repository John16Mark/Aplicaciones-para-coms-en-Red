import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

class Server {
    public static final int PUERTO = 8000;
    ServerSocket serverSocket;

    class Manejador extends Thread {
        protected Socket socket;
        DataOutputStream outStream;
        DataInputStream inStream;
        protected String nombreArchivo;

        public Manejador(Socket _socket) throws Exception {
            this.socket = _socket;
        }

        public void run() {
            try {
                outStream = new DataOutputStream(socket.getOutputStream());
                inStream = new DataInputStream(socket.getInputStream());
                byte[] buffer = new byte[50000];
                int size = inStream.read(buffer);
                if (size == -1) {
                    System.out.println("\033[91mConexión cerrada por el cliente o sin datos recibidos.\033[0m");
                    socket.close();
                    return;
                }
                String peticion = new String(buffer, 0, size);
                System.out.println("\033[93mCliente conectado desde:\033[0m "+socket.getInetAddress());
                System.out.println("\033[93mPor el puerto:\033[0m "+socket.getPort());
                System.out.println("\033[93mDatos:\033[0m "+peticion+"\r\n");

                StringTokenizer stringTokenizer = new StringTokenizer(peticion, "\n");
                String line = stringTokenizer.nextToken();
                String metodo = line.split(" ")[0].toUpperCase();

                switch(metodo) {
                    case "GET":
                        GET(line);
                        break;
                    case "POST":
                        POST(stringTokenizer);
                        break;
                    case "PUT":
                        PUT(stringTokenizer, line);
                        break;
                    case "DELETE":
                        DELETE(line);
                        break;
                    case "HEAD":
                        HEAD();
                        break;
                    default:
                        enviarError("501 Not Implemented", "Método no manejado");
                        break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void GET(String line) throws Exception{
            System.out.println("\033[94mMétodo GET\n\033[96mContenido:\033[0m ");
            if(line.indexOf("?") == -1) {
                getArchivo(line);
                if(nombreArchivo.compareTo("") == 0) {
                    sendArchivo("index.html", outStream);
                } else {
                    sendArchivo(nombreArchivo, outStream);
                }
            } else if (line.toUpperCase().startsWith("GET")) {
                StringTokenizer tokens = new StringTokenizer(line, "?");
                String req_a = tokens.nextToken();
                String req = tokens.nextToken();
                System.out.println("\033[92mToken 1:\033[0m "+req_a);
                System.out.println("\033[92mToken 2:\033[0m "+req);
                String parametros = req.substring(0, req.indexOf(" "))+"\n";
                System.out.println("\033[92mParámetros:\033[0m "+parametros);
                
                StringBuffer respuesta = new StringBuffer();
                respuesta.append("HTTP/1.0 200 Okay \n");
                String fecha= "Date: " + new Date()+" \n";
                respuesta.append(fecha);
                String tipo_mime = "Content-Type: text/html \n\n";
                respuesta.append(tipo_mime);
                respuesta.append("<html><head><title>SERVIDOR WEB</title></head>\n");
                respuesta.append("<body bgcolor=\"#AACCFF\"><center><h1><br>Parametros Obtenidos..</br></h1><h3><b>\n");
                respuesta.append(parametros);
                respuesta.append("</b></h3>\n");
                respuesta.append("</center></body></html>\n\n");
                System.out.println("\033[92mRespuesta:\033[0m "+respuesta);
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
            System.out.println("\033[94mMétodo POST\n\033[96mContenido:\033[0m "+contenido);
            String respuesta = "HTTP/1.0 200 OK\nContent-Type: text/plain\n\nDatos recibidos correctamente.\n";
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void PUT(StringTokenizer stringTokenizer, String primeraLinea) throws Exception {
            String directorio = "data";
            File dir = new File(directorio);
            if(!dir.exists())
                dir.mkdirs();
            
            String[] tokens = primeraLinea.split(" ");
            String uri = tokens[1];
            String nombreArchivo = uri.substring(1);

            System.out.println("\033[92mnombreArchivo:\033[0m " + nombreArchivo);
            if(nombreArchivo.isEmpty()) {
                enviarError("400 Bad Request", "Nombre de archivo no especificado");
                return;
            }

            File archivo = new File(directorio + File.separator + nombreArchivo);

            StringBuilder contenido = new StringBuilder();
            while(stringTokenizer.hasMoreTokens())
                contenido.append(stringTokenizer.nextToken()).append("\n");
            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                fos.write(contenido.toString().getBytes());
                fos.flush();
            }
            System.out.println("Archivo guardado: " + archivo.getAbsolutePath());


                System.out.println("\033[94mMétodo PUT\n\033[96mContenido:\033[0m "+contenido);
            String respuesta = "HTTP/1.0 200 OK\nContent-Type: text/plain\n\nRecurso creado o actualizado correctamente.\n";
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
            socket.close();
        }

        public void DELETE(String line) throws Exception {
            // TODO
        }

        public void HEAD() throws Exception {
            String respuesta = "HTTP/1.0 200 OK\n" +
                       "Content-Type: text/html\n" +
                       "Content-Length: 0\n\n";
            outStream.write(respuesta.getBytes());
            outStream.flush();
        }

        public void enviarError(String codigo, String mensaje) throws Exception{
            String respuesta = "HTTP/1.0 "+ codigo +"\n" +
                "Content-Type: text/plain\r\n".getBytes();
            outStream.write(respuesta.getBytes());
            outStream.flush();
            outStream.close();
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

        public void sendArchivo(String nombre, DataOutputStream out) {
            try {
                DataInputStream inTemp = new DataInputStream(new FileInputStream(nombre));
                byte[] buffer = new byte[1024];
                int x = 0;
                File file = new File(nombre);
                long size = file.length();
                long cont = 0;

                String sb = "";
                sb = sb+"HTTP/1.0 200 ok\n";
                sb = sb +"Server: Axel Server/1.0\n";
                sb = sb +"Date: " + new Date()+" \n";
                sb = sb +"Content-Type: text/html \n";
                sb = sb +"Content-Length: "+size+" \n";
                sb = sb +"\n";
                out.write(sb.getBytes());
                out.flush();
                while(cont < size) {
                    x = inTemp.read(buffer);
                    out.write(buffer,0, x);
                    cont=cont+x;
                    out.flush();
                
                
                }
                //bos.flush();
                inTemp.close();
                out.close();
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }
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