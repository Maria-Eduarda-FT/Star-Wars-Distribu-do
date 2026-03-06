package comum;

import java.io.Serializable;

public class Entidade implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private TipoEntidade tipo;
    private int x;
    private int y;
    private int vida;
    private int combustivel;
    private String faccao;

    public Entidade(String id, TipoEntidade tipo, int x, int y,
                    int vida, int combustivel) {
        this.id = id; this.tipo = tipo;
        this.x = x; this.y = y;
        this.vida = vida; this.combustivel = combustivel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public TipoEntidade getTipo() { return tipo; }
    public void setTipo(TipoEntidade tipo) { this.tipo = tipo; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getVida() { return vida; }
    public void setVida(int vida) { this.vida = vida; }

    public int getCombustivel() { return combustivel; }
    public void setCombustivel(int combustivel) { this.combustivel = combustivel; }


    @Override
    public String toString() {
        return String.format("%s [%s] (%d,%d) Vida:%d Comb:%d",
                id, tipo.getNome(), x, y, vida, combustivel);
    }
}