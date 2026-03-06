package comum.mensagens.eventos;
public class EventoVitoria extends Evento {
    private final String mensagem;

    public EventoVitoria(String remetente, String mensagem) {
        super(remetente);
        this.mensagem = mensagem;
    }

    public String getMensagem() { return mensagem; }
}