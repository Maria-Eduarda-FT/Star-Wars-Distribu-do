package comum.mensagens.eventos;
public class EventoCombustivelBaixo extends Evento {
    private final String naveId;
    private final int combustivelAtual;

    public EventoCombustivelBaixo(String remetente, String naveId, int combustivelAtual) {
        super(remetente); this.naveId = naveId;
        this.combustivelAtual = combustivelAtual;
    }

    public String getNaveId() { return naveId; }
    public int getCombustivelAtual() { return combustivelAtual; }
}