package comum.mensagens.eventos;
// será chamado quando os jogadores perdem
public class EventoDerrota extends Evento {
    private final String mensagem;
    private final String naveId;

    public EventoDerrota(String remetente, String naveId, String mensagem) {
        super(remetente);
        this.naveId = naveId;
        this.mensagem = mensagem;
    }

    public String getNaveId() { return naveId; }
    public String getMensagem() { return mensagem; }
}