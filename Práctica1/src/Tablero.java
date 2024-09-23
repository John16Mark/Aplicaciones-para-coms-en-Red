import java.io.Serializable;
import java.util.Random;

public class Tablero implements Serializable {
    
    public int n;
    public int m;
    public int n_casillas;
    public int n_descubiertas = 0;
    public int n_minas;
    private int tablero_datos[][];
    public char tablero_publico[][];
    private int minas_encontradas = 0;
    public int banderas_colocadas = 0;
    public enum Estado {
        EN_PROGRESO,
        GANO,
        PERDIO;
    }
    public Estado estado = Estado.EN_PROGRESO;
    //private ArrayList<int[2]> pos_minas;

    public Tablero(int d) {
        if(d == 1) {
            this.n_minas = 10;
            this.n = 9;
            this.m = 9;
        } else if(d == 2) {
            // TODO
            this.n_minas = 40;
            this.n = 16;
            this.m = 16;
        } else {
            // TODO
            this.n_minas = 99;
            this.n = 16;
            this.m = 30;
        }
        tablero_datos = new int[this.n][this.m];
        tablero_publico = new char[this.n][this.m];
        n_casillas = this.n*this.m;
        setMinas();
        setNumeros();
        setTableroPublico();
    }

    private void setMinas() {
        int minas = 0;
        while(minas < this.n_minas) {
            Random rand1 = new Random();
            Random rand2 = new Random();
            int x = rand1.nextInt(this.n);
            int y = rand2.nextInt(this.m);
            if(this.tablero_datos[x][y] != -1) {
                this.tablero_datos[x][y] = -1;
                minas++;
            }
        }
    }

    private void setNumeros() {
        for(int i=0; i<this.n; i++) {
            for(int j=0; j<this.m; j++) {
                if(this.tablero_datos[i][j] != -1) {
                    int minas = 0;
                    boolean arriba = i-1 >= 0;
                    boolean abajo = i+1 < this.n;
                    boolean izquierda = j-1 >= 0;
                    boolean derecha = j+1 < this.m;
                    if(arriba) {
                        if(this.tablero_datos[i-1][j] == -1)
                            minas++;
                        if(izquierda && this.tablero_datos[i-1][j-1] == -1)
                            minas++;
                        if(derecha && this.tablero_datos[i-1][j+1] == -1)
                            minas++;
                    }
                    if(abajo) {
                        if(this.tablero_datos[i+1][j] == -1)
                            minas++;
                        if(izquierda && this.tablero_datos[i+1][j-1] == -1)
                            minas++;
                        if(derecha && this.tablero_datos[i+1][j+1] == -1)
                            minas++;
                    }
                    if(izquierda && this.tablero_datos[i][j-1] == -1)
                        minas++;
                    if(derecha && this.tablero_datos[i][j+1] == -1)
                        minas++;
                    this.tablero_datos[i][j] = minas;
                }
            }
        }
    }

    private void setTableroPublico() {
        for(int i=0; i<this.n; i++) {
            for(int j=0; j<this.m; j++) {
                this.tablero_publico[i][j] = '-';
            }
        }
    }

    public boolean destapar(int x, int y) {
        if(x<0 || x>=this.n || y<0 || y>=this.m)
            return false;
        if(this.tablero_datos[x][y] == -1) {
            estado = Estado.PERDIO;
            return true;
        }
        revelar(x, y);
        return true;
    }

    public void revelar(int x, int y) {
        // Si estoy fuera de rango, ignorar
        if(x<0 || x>=this.n || y<0 || y>=this.m)
            return;
        int casilla = this.tablero_datos[x][y];
        // Si la casilla ya fue revelada, ignorar
        if(casilla == -1 || this.tablero_publico[x][y] != '-')
            return;
        // Si la casilla es una adyacente a bomba, revelar
        else if(casilla != 0) {
            this.tablero_publico[x][y] = (char)('0' + casilla);
            this.n_descubiertas++;
        // Si la casilla no es adyacente a bomba, revelar y todas las de alrededor
        } else {
            this.tablero_publico[x][y] = ' ';
            this.n_descubiertas++;
            revelar(x-1, y);
            revelar(x-1, y+1);
            revelar(x, y+1);
            revelar(x+1, y+1);
            revelar(x+1, y);
            revelar(x+1, y-1);
            revelar(x, y-1);
            revelar(x-1, y-1);
        }
    }

    public boolean colocar(int x, int y) {
        // Si estoy fuera de rango, ignorar
        if(x<0 || x>=this.n || y<0 || y>=this.m)
            return false;
        // Si no es una casilla desconocida, ignorar
        if(this.tablero_publico[x][y] != '-')
            return false;
        
        this.banderas_colocadas++;
        this.n_descubiertas++;
        this.tablero_publico[x][y] = 'i';
        // Si la coloqué correctamente, aumentar el contador
        if(this.tablero_datos[x][y] == -1) {
            this.minas_encontradas++;
            if(this.banderas_colocadas == n_minas && this.minas_encontradas == n_minas && this.n_descubiertas == this.n_casillas)
                estado = Estado.GANO;
        }
        return true;
    }

    public boolean quitar(int x, int y) {
        // Si estoy fuera de rango, ignorar
        if(x<0 || x>=this.n || y<0 || y>=this.m)
            return false;
        // Si no es una casilla con bandera, ignorar
        if(this.tablero_publico[x][y] != 'i')
            return false;
        
        this.banderas_colocadas--;
        this.n_descubiertas--;
        this.tablero_publico[x][y] = '-';
        // Si quité una que tenía mina, disminuir el contador
        if(this.tablero_datos[x][y] == -1) {
            this.minas_encontradas--;
        }
        if(this.banderas_colocadas == n_minas && this.minas_encontradas == n_minas && this.n_descubiertas == this.n_casillas)
            estado = Estado.GANO;
        return true;
    }

    public boolean enProgreso() {
        return estado == Estado.EN_PROGRESO;
    }
    public boolean gano() {
        return estado == Estado.GANO;
    }
    public boolean perdio() {
        return estado == Estado.PERDIO;
    }

    public void printTablero(int tipo) {
        if(tipo == 1) {
            for(int i=0; i<n; i++) {
                for(int j=0; j<m; j++) {
                    System.out.printf("%-3d", this.tablero_datos[i][j]);
                }
                System.out.println("");
            }
        } else if(tipo == 2) {
            System.out.print("    ");
            for(int j=0; j<m; j++) {
                System.out.printf("%-3d", j);
            }
            System.out.print("\n    ");
            for(int j=0; j<m; j++) {
                System.out.printf("___");
            }
            System.out.print("\n");
            for(int i=0; i<n; i++) {
                System.out.printf("%-3d|", i);
                for(int j=0; j<m; j++) {
                    if(this.tablero_datos[i][j] != 0) {
                        switch(this.tablero_datos[i][j]) {
                            case -1:
                                System.out.print("\033[0m");
                                break;
                            case 1:
                                System.out.print("\033[34m");
                                break;
                            case 2:
                                System.out.print("\033[32m");
                                break;
                            case 3:
                                System.out.print("\033[31m");
                                break;
                            case 4:
                                System.out.print("\033[38;2;0;0;132m");
                                break;
                            case 5:
                                System.out.print("\033[38;2;132;0;0m");
                                break;
                            case 6:
                                System.out.print("\033[38;2;0;130;132m");
                                break; 
                            case 7:
                                System.out.print("\033[38;2;132;0;132m");
                                break; 
                            case 8:
                                System.out.print("\033[38;2;117;117;117m");
                                break;
                        }
                        System.out.printf("%-3d", this.tablero_datos[i][j]);
                    } else
                        System.out.print("   ");
                    System.out.print("\033[0m");
                }
                System.out.println("");
            }
        } else if(tipo == 3) {
            System.out.print("    ");
            for(int j=0; j<m; j++) {
                System.out.printf("%-3d", j);
            }
            System.out.print("\n    ");
            for(int j=0; j<m; j++) {
                System.out.printf("___");
            }
            System.out.print("\n");
            for(int i=0; i<n; i++) {
                System.out.printf("%-3d|", i);
                for(int j=0; j<m; j++) {
                    System.out.printf("%-3c", this.tablero_publico[i][j]);
                }
                System.out.println("");
            }
        }
    }
}
