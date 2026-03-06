package comum.mensagens.eventos;

public class EventoNaveRemovida extends Evento {

    private final String naveId;

    public EventoNaveRemovida(String remetente, String naveId) {
        super(remetente);
        this.naveId = naveId;
    }

    public String getNaveId() {
        return naveId;
    }
}
