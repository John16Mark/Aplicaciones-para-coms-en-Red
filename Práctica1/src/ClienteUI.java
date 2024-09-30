import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClienteUI {
    static ObjectOutputStream write;
    static ObjectInputStream read;
    static Tablero tableroC;
    static Ventana ventana;
    static volatile boolean game = true;
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        
        try {
            int pto = 8000;
            String dir = "127.0.0.1";

            // crea un socket y establece conexión con el servidor
            Socket cl = new Socket(dir,pto);
            System.out.println("Conexion con el servidor establecida...");

            //Flujos para enviar y recibir datos
            write = new ObjectOutputStream(cl.getOutputStream());
            read = new ObjectInputStream(cl.getInputStream());

            // recibe el menu del servidor
            String menu = read.readUTF();
            System.out.println(menu);

            // Se lee la elección del usuario y se envía al servidor
            int dificultad = in.nextInt();
            write.writeInt(dificultad);
            write.flush();

            // recibe el tablero y lo imprime
            tableroC = (Tablero) read.readObject();
            tableroC.printTablero(3);

            ventana = new Ventana(tableroC.n, tableroC.m);
            while(tableroC.enProgreso() && game) {
                
            }
            System.out.println("salio del bucle");
            if(tableroC.gano()) {
                ventana.setTitulo("GANÓ EL JUEGO OwO");
                String win = read.readUTF();
                System.out.println(win);
            } else if(tableroC.perdio()) {
                ventana.setTitulo("PERDIÓ EL JUEGO :(");
                String lose = read.readUTF();
                System.out.println(lose);
            }

            cl.close();
            in.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    static void mandarDatos(int op, int coord_x, int coord_y) {
        // lee la opción y coordenadas
        int opcion = op;
        int x = coord_x;
        int y = coord_y;

        try {
            write.writeInt(opcion);
            write.writeInt(x);
            write.writeInt(y);
            write.flush();
            
            // recibe el tablero y lo imprime
            try {
                tableroC = (Tablero) read.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            tableroC.printTablero(3);
            ventana.actualizar(tableroC.tablero_publico, tableroC.n, tableroC.m);
            System.out.println("?");
            if(!tableroC.enProgreso()) {
                System.out.println("Acaba");
                ventana.fin();
                game = false;
                System.out.println(game);
            } else {
                System.out.println("Continua");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}