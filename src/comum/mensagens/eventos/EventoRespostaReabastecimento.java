package comum.mensagens.eventos;

public class EventoRespostaReabastecimento extends Evento {
    private final String naveRequisitante;
    private final boolean ok;

    public EventoRespostaReabastecimento(String remetente, String naveRequisitante, boolean ok) {
        super(remetente);
        this.naveRequisitante = naveRequisitante;
        this.ok = ok;
    }

    public String getDestinatario() { return naveRequisitante; }
    public boolean isOk() { return ok; }
}