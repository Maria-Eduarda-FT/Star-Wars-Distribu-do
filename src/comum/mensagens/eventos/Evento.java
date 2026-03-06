package comum.mensagens.eventos;
import comum.mensagens.Mensagem;

public abstract class Evento extends Mensagem {
    protected Evento(String remetente) {
        super(remetente);
    }
}
