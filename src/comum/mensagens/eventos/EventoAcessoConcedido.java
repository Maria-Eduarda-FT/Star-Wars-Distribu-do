package comum.mensagens.eventos;
public class EventoAcessoConcedido extends Evento {
    private final String naveId;
    private final String baseId;

    public EventoAcessoConcedido(String remetente, String naveId, String baseId) {
        super(remetente);
        this.naveId = naveId;
        this.baseId = baseId;
    }

    public String getNaveId() { return naveId; }
    public String getBaseId() { return baseId; }
}