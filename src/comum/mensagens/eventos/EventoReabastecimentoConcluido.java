package comum.mensagens.eventos;
public class EventoReabastecimentoConcluido extends Evento {
    private final String naveId;
    private final String baseId;
    private final int combustivelNovo;
    private final int vidaNova;

    public EventoReabastecimentoConcluido(String remetente, String naveId,
                                          String baseId, int combustivelNovo, int vidaNova) {
        super(remetente);
        this.naveId = naveId;
        this.baseId = baseId;
        this.combustivelNovo = combustivelNovo;
        this.vidaNova = vidaNova;
    }

    public String getNaveId() { return naveId; }
    public String getBaseId() { return baseId; }
    public int getCombustivelNovo() { return combustivelNovo; }
    public int getVidaNova() { return vidaNova; }
}