package comum.mensagens.eventos;
import java.util.List;

public class EventoNavesInimigasCriadas extends Evento {
    private final int quantidade;
    private final String localizacao;
    private final List<String> navesIds;

    public EventoNavesInimigasCriadas(String remetente, int quantidade,
                                      String localizacao, List<String> navesIds) {
        super(remetente);
        this.quantidade = quantidade;
        this.localizacao = localizacao;
        this.navesIds = navesIds;
    }

    public int getQuantidade() { return quantidade; }
    public String getLocalizacao() { return localizacao; }
    public List<String> getNavesIds() { return navesIds; }
}