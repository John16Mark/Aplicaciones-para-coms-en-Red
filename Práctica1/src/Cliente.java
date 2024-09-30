import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {
    static ObjectOutputStream write;
    static ObjectInputStream read;
    
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
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
            String menu = read.readUTF();
            System.out.println(menu);

            // Se lee la elección del usuario y se envía al servidor
            int dificultad = in.nextInt();
            write.writeInt(dificultad);
            write.flush();

            // recibe el tablero y lo imprime
            Tablero tableroC = (Tablero) read.readObject();
            tableroC.printTablero(3);

            while(tableroC.enProgreso()) {
                String seleccion = "1. Destapar casilla\n2. Colocar bandera\n3. Quitar bandera\n   Opción: ";
                System.out.print(seleccion);

                // lee la opción y coordenadas
                int opcion = in.nextInt();
                System.out.print("fila: ");
                int x = in.nextInt();
                System.out.print("colu: ");
                int y = in.nextInt();

                // envía la opción y coordenadas
                write.writeInt(opcion);
                write.writeInt(x);
                write.writeInt(y);
                write.flush();

                // recibe el tablero y lo imprime
                Tablero t = (Tablero) read.readObject();
                t.printTablero(3);

                if(t.gano()) {
                    String win = read.readUTF();
                    System.out.println(win);
                    break;
                } else if(t.perdio()) {
                    String lose = read.readUTF();
                    System.out.println(lose);
                    break;
                }
            }

            cl.close();
            in.close();
        } catch(Exception e) {
            e.printStackTrace();
        }

        // Tablero t = new Tablero(1);
        // t.printTablero(2);
    }

}