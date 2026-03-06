package comum.mensagens.eventos;

import comum.Entidade;

public class EventoNaveEntrou extends Evento {
    private final Entidade nave;

    public EventoNaveEntrou(String remetente, Entidade nave) {
        super(remetente);
        this.nave = nave;
    }
    public Entidade getId() {
        return nave;
    }
    public Entidade getNave() { return nave; }
}