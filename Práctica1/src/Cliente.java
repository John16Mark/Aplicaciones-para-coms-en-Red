import java.io.*;
import java.net.*;

public class Cliente {
    public static void main(String[] args) {
        try {
            int pto = 8000;
            String dir = "127.0.0.1";

            // crea un socket y establece conexión con el servidor
            Socket cl = new Socket(dir,pto);
            System.out.println("Conexion con el servidor establecida...");

            //Flujos para enviar y recibir datos
            ObjectOutputStream write = new ObjectOutputStream(cl.getOutputStream());
            ObjectInputStream read = new ObjectInputStream(cl.getInputStream());

            // recibe el menu del servidor
            String menu = (String) read.readObject();
            System.out.println(menu);

            // se lee la elección del usuario
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String op = br.readLine();

            // envia la opción al servidor
            write.writeObject(op);
            write.flush();

            // recibe el tablero y lo imprime
            Tablero tableroC = (Tablero) read.readObject();
            tableroC.printTablero(3);

            while(true) {
                // recibe e imprime las opciones
                String seleccion = (String) read.readObject();
                System.out.println(seleccion);

                // lee la opción y coordenadas
                String opcion = br.readLine();
                String x = br.readLine();
                String y = br.readLine();

                // envía la opción y coordenadas
                write.writeObject(opcion);
                write.writeObject(x);
                write.writeObject(y);
                write.flush();

                // recibe el tablero y lo imprime
                Tablero t = (Tablero) read.readObject();
                t.printTablero(3);

                if(t.gano()) {
                    String win = (String) read.readObject();
                    System.out.println(win);
                    break;
                } else if(t.perdio()) {
                    String lose = (String) read.readObject();
                    System.out.println(lose);
                    break;
                }
            }

            cl.close();

        } catch(Exception e) {
            e.printStackTrace();
        }

        // Tablero t = new Tablero(1);
        // t.printTablero(2);
    }
}