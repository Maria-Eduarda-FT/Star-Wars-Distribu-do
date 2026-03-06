package comum.mensagens.eventos;
import comum.Entidade;

public class EventoProximidadeNaves extends Evento {

    public final Entidade outraNave;
    public final boolean aliada;

    public EventoProximidadeNaves(String remetente, Entidade outraNave, boolean aliada) {
        super(remetente);
        this.outraNave = outraNave;
        this.aliada = aliada;
    }

    public Entidade getOutraNave() {
        return outraNave;
    }

    public boolean isAliada() {
        return aliada;
    }
}
