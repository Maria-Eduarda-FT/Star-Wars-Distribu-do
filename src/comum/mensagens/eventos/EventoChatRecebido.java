package comum.mensagens.eventos;

public class EventoChatRecebido extends Evento {
    private final String texto;
    public EventoChatRecebido(String remetente, String texto) {
        super(remetente);this.texto = texto;
    }
    public String getTexto() {return texto;}
}
