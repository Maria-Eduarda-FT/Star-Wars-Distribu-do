package comum.mensagens.eventos;

public class EventoEnderecoBase extends Evento {
    private final String baseId;
    private final String host;
    private final int porta;

    public EventoEnderecoBase(String remetente, String baseId, String host, int porta) {
        super(remetente);
        this.baseId = baseId;
        this.host = host;
        this.porta = porta;
    }

    public String getBaseId() { return baseId; }
    public String getHost() { return host; }
    public int getPorta() { return porta; }
}