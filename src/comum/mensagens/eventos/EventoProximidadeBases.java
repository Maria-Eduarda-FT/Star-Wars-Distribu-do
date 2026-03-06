package comum.mensagens.eventos;
import comum.Entidade;

public class EventoProximidadeBases extends Evento {
    private final Entidade base;

    public EventoProximidadeBases(String remetente, Entidade base) {
        super(remetente);
        this.base = base;
    }

    public Entidade getBase() {
        return base;
    }
}
