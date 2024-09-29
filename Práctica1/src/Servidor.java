import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Servidor {
    public static void main(String[] args) {

        try {
            // crea un socket y lo asocia a un puerto local
            int pto = 8000;
            ServerSocket s = new ServerSocket(pto);
            s.setReuseAddress(true); // enable/disable the SO_REUSEADDR socket option
            
            System.out.println("Servidor iniciado, esperando conexión...");

            for(;;) {
                // Espera a que el cliente se conecte
                Socket cl = s.accept();
                System.out.println("Cliente conectado desde puerto " + cl.getPort());

                //Flujos para enviar y recibir datos
                ObjectOutputStream write = new ObjectOutputStream(cl.getOutputStream());
                ObjectInputStream read = new ObjectInputStream(cl.getInputStream());

                // --------- Menú
                String menu =   "Juego de Buscaminas\n" +
                                "Elije la dificultad:\n" +
                                "1. Principiante\n" +
                                "2. Intermedio\n" +
                                "3. Experto\n";
                
                write.writeObject(menu);
                write.flush();

                String op = (String) read.readObject();
                System.out.println("El cliente selecciono: " + op);

                // Se crea el tablero con la dificultad
                Tablero t = new Tablero(Integer.parseInt(op));

                // se manda tablero  
                write.writeObject(t);
                write.flush();

                while(t.enProgreso()) {
                    // se manda menu de opciones
                    String seleccion = "1. Destapar casilla\n2. Colocar bandera\n3. Quitar bandera\n   Opción: ";
                    write.writeObject(seleccion);
                    write.flush();
                
                    // se recibe opcion y coordenadas
                    String opcion = (String) read.readObject();
                    String x = (String) read.readObject();
                    String y = (String) read.readObject();

                    clrscr();

                    t.printTablero(2);
                    System.out.println("El cliente selecciono: " + opcion);
                    System.out.println("Con coordenadas: " + x + "," + y);
                    
                    //
                    if(Integer.parseInt(opcion) == 1) {
                        t.destapar(Integer.parseInt(x), Integer.parseInt(y));
                    } else if(Integer.parseInt(opcion) == 2) {
                        t.colocar(Integer.parseInt(x), Integer.parseInt(y));
                    } else if(Integer.parseInt(opcion) == 3) {
                        t.quitar(Integer.parseInt(x), Integer.parseInt(y));
                    }

                    t.printTablero(3);
                    // se manda tablero a cliente
                    write.reset();
                    write.writeObject(t);
                    write.flush();

                    if(t.gano()) {
                        clrscr();
                        System.out.println("EL CLIENTE GANO EL JUEGO");
                        String win = "\033[92mGANO EL JUEGO\033[0m";
                        write.writeObject(win);
                        write.flush();
                        break;
                    } else if(t.perdio()) {
                        clrscr();
                        System.out.println("EL CLIENTE PERDIO EL JUEGO");
                        String lose = "\033[91mPERDIO EL JUEGO\033[0m";
                        write.writeObject(lose);
                        write.flush();
                        break;
                    }
                }

                
                // Scanner in = new Scanner(System.in); // Leer de la consola
                // System.out.println("");
                // t.printTablero(3);
                // ButtonGridFrame bgf = new ButtonGridFrame(16, 30);
                // while(t.enProgreso()) {
                //     System.out.println("1. Destapar casilla\n2. Colocar bandera\n3. Quitar bandera\n   Opción: ");
                //     int opcion = in.nextInt();
                //     int x = in.nextInt();
                //     int y = in.nextInt();
                //     clrscr();
                //     t.printTablero(2);
                //     if(opcion == 1) {
                //         t.destapar(x, y);
                //     } else if(opcion == 2) {
                //         t.colocar(x, y);
                //     } else if(opcion == 3) {
                //         t.quitar(x, y);
                //     }
                //     t.printTablero(3);
                //     if(t.gano()) {
                //         System.out.println("\033[92mGANO EL JUEGO\033[0m");
                //         break;
                //     } else if(t.perdio()) {
                //         System.out.println("\033[91mPERDIO EL JUEGO\033[0m");
                //         break;
                //     } 
                // }

                cl.close();
            }
        }catch(Exception e) {
            e.printStackTrace();
        }
        
    }

    public static void clrscr() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                // Para sistemas Unix (Linux, macOS), puedes usar "clear"
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
