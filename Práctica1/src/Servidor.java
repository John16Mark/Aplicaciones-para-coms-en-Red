import java.util.Scanner;

public class Servidor {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        Tablero t = new Tablero(2);
        t.printTablero(2);
        System.out.println("");
        t.printTablero(3);
        ButtonGridFrame bgf = new ButtonGridFrame(16, 30);
        while(t.enProgreso()) {
            System.out.print("1. Destapar casilla\n2. Colocar bandera\n3. Quitar bandera");
            int opcion = in.nextInt();
            int x = in.nextInt();
            int y = in.nextInt();
            clrscr();
            t.printTablero(2);
            if(opcion == 1) {
                t.destapar(x, y);
            } else if(opcion == 2) {
                t.colocar(x, y);
            } else if(opcion == 3) {
                t.quitar(x, y);
            }
            t.printTablero(3);
            if(t.gano()) {
                System.out.println("\033[92mGANO EL JUEGO");
                break;
            } else if(t.perdio()) {
                System.out.println("\033[91mPERDIO EL JUEGO");
                break;
            } 
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
