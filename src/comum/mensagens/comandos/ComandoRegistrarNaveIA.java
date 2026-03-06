package comum.mensagens.comandos;

import comum.Entidade;

public class ComandoRegistrarNaveIA extends Comando {
    private final Entidade nave;

    public ComandoRegistrarNaveIA(Entidade nave) {
        super(nave.getId());
        this.nave = nave;
    }

    public Entidade getNave() { return nave; }
}