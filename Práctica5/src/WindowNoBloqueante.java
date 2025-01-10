import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.channels.DatagramChannel;
import java.nio.file.Path;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

class WindowNoBloqueante extends JFrame {

    DatagramChannel channel;

    private JPanel contentPane;
    private JPanel panelTituloBotones;
    private JPanel panelTitulo;
    private JPanel panelBtnIzquierda;
    private JPanel panelBtnDerecha;

    private JPanel panelDirectorio;
    private JPanel panelBtnDirectorios;

    private JLabel titulo;
    private JEditorPane editorPane;
    private JButton btnSubir;
    private JButton btnBajar;
    private JButton btnCrear;
    private JButton btnRenombrar;
    private JButton btnBorrar;

    private JButton btnAvanzar;
    private JButton btnRegresar;
    private JLabel directorio;

    final static int WIDTH = 1000;
    final static int HEIGHT = 600;

    final static Dimension tamBtn = new Dimension(250, 150);

    public WindowNoBloqueante(DatagramChannel channel, Path rutaDirectorio) {
        this.channel = channel;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, WIDTH, HEIGHT);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(15, 0));
		
        // Panel superior
		panelTituloBotones = new JPanel();
		contentPane.add(panelTituloBotones, BorderLayout.NORTH);
		panelTituloBotones.setLayout(new BorderLayout(0, 0));
		
        // Título
		panelTitulo = new JPanel();
		panelTituloBotones.add(panelTitulo, BorderLayout.NORTH);
		titulo = new JLabel("Nube");
        titulo.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 48)); // NOI18N
		panelTitulo.add(titulo);
		
        // Botones izquierda
		panelBtnIzquierda = new JPanel();
		panelTituloBotones.add(panelBtnIzquierda, BorderLayout.WEST);
		btnSubir = new JButton("Subir Archivo");
		panelBtnIzquierda.add(btnSubir);
		btnBajar = new JButton("Bajar Archivo");
		panelBtnIzquierda.add(btnBajar);
		btnCrear = new JButton("Crear carpeta");
		panelBtnIzquierda.add(btnCrear);
        btnRenombrar = new JButton("Renombrar archivo");
		panelBtnIzquierda.add(btnRenombrar);
		
        // Botones derecha
		panelBtnDerecha = new JPanel();
		panelTituloBotones.add(panelBtnDerecha, BorderLayout.EAST);
		btnBorrar = new JButton("Borrar archivo/carpeta");
		panelBtnDerecha.add(btnBorrar);

        // panelBtnDirectorios
        panelDirectorio = new JPanel();
        panelTituloBotones.add(panelDirectorio, BorderLayout.SOUTH);
        panelDirectorio.setLayout(new BorderLayout(0, 0));
		
        panelBtnDirectorios = new JPanel();
        panelDirectorio.add(panelBtnDirectorios, BorderLayout.EAST);

        btnAvanzar = new JButton("Avanzar directorio");
        panelBtnDirectorios.add(btnAvanzar);
        btnRegresar = new JButton("Regresar directorio");
        panelBtnDirectorios.add(btnRegresar);
        
        directorio = new JLabel(rutaDirectorio.toString());
        directorio.setFont(new java.awt.Font("Franklin Gothic Demi Cond", 0, 12)); // NOI18N
        panelDirectorio.add(directorio, BorderLayout.WEST);
		
        // Texto
        int epWidth = (2*WIDTH)/3;
        int epHeight = (2*HEIGHT)/3-50;
        editorPane = new JEditorPane();
        editorPane.setContentType("text/html"); // Puede ser texto simple o HTML
        editorPane.setText("<html><h1>Bienvenido</h1><p>Este es un ejemplo de JEditorPane.</p></html>");
        editorPane.setPreferredSize(new Dimension(epWidth, epHeight));
        editorPane.setEditable(false);

        // Colocar el JEditorPane en un JScrollPane
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setPreferredSize(null);
        
        JPanel panelContenido = new JPanel(new BorderLayout());
        panelContenido.setBackground(new Color(200,15,200));
        panelContenido.add(scrollPane, BorderLayout.CENTER);
        panelContenido.setPreferredSize(new Dimension(epWidth, epHeight));
        contentPane.add(panelContenido, BorderLayout.SOUTH);

        btnSubir.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.subirArchivo(channel);
            } 
        });

        btnBajar.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.bajarArchivo(channel);
            } 
        });

        btnCrear.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.crearDirectorio(channel);
            } 
        });

        btnRenombrar.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.renombrarArchivo(channel);
            } 
        });

        btnBorrar.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.eliminarArchivo(channel);
            } 
        });

        btnAvanzar.addActionListener(new ActionListener() { 
            public void actionPerformed(ActionEvent e) { 
                ClienteNoBloqueante.avanzarDirectorio(channel);
            } 
        });

        btnRegresar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ClienteNoBloqueante.regresarDirectorio(channel);
            }
        });
        
        setVisible(true);
    }

    public void actualizarDirectorio(String camino, String contenido) {
        String html = "<html>\n";
        html += "<body style='width: 100%; margin: 0; padding: 0; background-color: #eeeeff'>\n";
        html += "<table style='width: 100%; background-color: #eeeea0'>\n";
        html += "<tr style='width: 100%'>\n"+
                "\t<th style='width: 10%; background-color: #0A55D9; color: #ffffff'>dir</th>\n"+
                "\t<th style='width: 90%; background-color: #0A55D9; color: #ffffff'>Nombre</th>\n"+
                "</tr>\n";
        if(!contenido.equals("")) {
            File file = new File("./img/folder.png");
            String parts[] = contenido.split("\\?");
            // Ordenar si es carpeta o no
            Arrays.sort(parts, (a, b) -> {
                boolean conditionA = !(a.contains(".") && a.lastIndexOf('.') != a.length() - 1);
                boolean conditionB = !(b.contains(".") && b.lastIndexOf('.') != b.length() - 1);

                if (conditionA && !conditionB) {
                    return -1;
                } else if (!conditionA && conditionB) {
                    return 1;
                } else {
                    return a.compareToIgnoreCase(b);
                }
            });

            int cont=0;
            for (String elemento : parts) {
                html += "<tr style='width:100%;";
                if(cont%2 == 0)
                    html+=" background-color: #ffffff'>\n";
                else
                    html+=" background-color: #efefef'>\n";
                html += "\t<td>";
                if (!(elemento.contains(".") && elemento.lastIndexOf('.') != elemento.length() - 1)) {
                    if (file.exists()) {
                        String absolutePath = file.toURI().toString();
                        html += "<img src='" + absolutePath + "' width='30' height='30'>";
                    } else {
                        System.out.println("La imagen no se encontró: " + file.getAbsolutePath());
                        html += "";
                    }
                } else {
                    html += "";
                }
                html += "</td>\n"+
                        "\t<td> <pre>"+elemento+"</pre> </td>\n"+
                        "</tr>\n";
                        System.out.println(elemento+"_");
                cont++;
            }
        }
        html+="</table></body></html>";
        editorPane.setText(html);
        directorio.setText(".\\"+camino);
    }
}