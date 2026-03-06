package comum.mensagens.eventos;
import comum.EstadoUniverso;

public class EventoEstadoUniverso extends Evento {
    private final EstadoUniverso estado;

    public EventoEstadoUniverso(String remetente, EstadoUniverso estado) {
        super(remetente);
        this.estado = estado;
    }

    public EstadoUniverso getEstado() {
        return estado;
    }
}