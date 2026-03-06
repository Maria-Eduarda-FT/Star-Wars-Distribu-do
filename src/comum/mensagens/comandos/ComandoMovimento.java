package comum.mensagens.comandos;

public class ComandoMovimento extends Comando {
    private final Direcao direcao;

    public ComandoMovimento(String remetente, Direcao direcao) {
        super(remetente);
        this.direcao = direcao;
    }

    public Direcao getDirecao() {
        return direcao;
    }
}
