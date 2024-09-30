import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

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
                                "\033[92m1. Principiante\n" +
                                "\033[93m2. Intermedio\n" +
                                "\033[91m3. Experto\033[0m\n";
                
                write.writeUTF(menu);
                write.flush();

                int dificultad = read.readInt();
                System.out.println("El cliente selecciono: " + dificultad);

                // Se crea el tablero con la dificultad
                Tablero t = new Tablero(dificultad);

                // se manda tablero  
                write.writeObject(t);
                write.flush();

                while(t.enProgreso()) {
                    int opcion = read.readInt();
                    int x = read.readInt();
                    int y = read.readInt();

                    clrscr();

                    t.printTablero(2);
                    System.out.println("El cliente selecciono: " + opcion);
                    System.out.println("Con coordenadas: " + x + "," + y);
                    
                    //
                    if(opcion == 1) {
                        t.destapar(x, y);
                    } else if(opcion == 2) {
                        t.colocar(x, y);
                    } else if(opcion == 3) {
                        t.quitar(x, y);
                    }

                    t.printTablero(3);
                    // se manda tablero a cliente
                    write.reset();
                    write.writeObject(t);
                    write.flush();
                }

                if(t.gano()) {
                    clrscr();
                    System.out.println("EL CLIENTE GANO EL JUEGO");
                    String win = "\033[92mGANO EL JUEGO\033[0m";
                    write.writeUTF(win);
                    write.flush();
                } else if(t.perdio()) {
                    clrscr();
                    System.out.println("EL CLIENTE PERDIO EL JUEGO");
                    String lose = "\033[91mPERDIO EL JUEGO\033[0m";
                    write.writeUTF(lose);
                    write.flush();
                }

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
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
