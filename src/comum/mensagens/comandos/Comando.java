package comum.mensagens.comandos;
import comum.mensagens.Mensagem;

public abstract class Comando extends Mensagem {
    protected Comando(String remetente) {
        super(remetente);
    }
}
