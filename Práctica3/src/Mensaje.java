import java.net.InetSocketAddress;

import com.google.gson.Gson;

public class Mensaje {
    private String tipo;
    private String usuario;
    private String contenido;
    private String destinatario;

    public Mensaje (String tipo, String usuario, String contenido, String destinatario) {
        this.tipo = tipo;
        this.usuario = usuario;
        this.contenido = contenido;
        this.destinatario = destinatario;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Mensaje fromJson(String json) {
        return new Gson().fromJson(json, Mensaje.class);
    }
}
