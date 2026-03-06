package comum.mensagens.comandos;

public class ComandoAtacar extends Comando {
    private final int xAlvo;
    private final int yAlvo;

    public ComandoAtacar(String remetente, int xAlvo, int yAlvo) {
        super(remetente);
        this.xAlvo = xAlvo;
        this.yAlvo = yAlvo;
    }

    public int getXAlvo() { return xAlvo; }
    public int getYAlvo() { return yAlvo; }
}