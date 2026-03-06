package comum.mensagens.comandos;
import comum.Entidade;

public class ComandoRegistrar extends Comando {
    private final Entidade nave;

    public ComandoRegistrar(String remetente, Entidade nave) {
        super(remetente);
        this.nave = nave;
    }

    public Entidade getNave() {
        return nave;
    }
}