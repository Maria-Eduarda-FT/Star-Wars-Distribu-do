package comum.mensagens.eventos;

public class EventoReabastecimento extends Evento {
    private final String naveId;
    private final boolean sucesso;
    private final String motivo;
    private final int combustivelNovo;
    private final int vidaNova;

    public EventoReabastecimento(String remetente, String naveId,
                                         boolean sucesso, String motivo,
                                         int combustivelNovo, int vidaNova) {
        super(remetente);
        this.naveId = naveId;
        this.sucesso = sucesso;
        this.motivo = motivo;
        this.combustivelNovo = combustivelNovo;
        this.vidaNova = vidaNova;
    }

    public String getNaveId() { return naveId; }
    public boolean isSucesso() { return sucesso; }
    public String getMotivo() { return motivo; }
    public int getCombustivelNovo() { return combustivelNovo; }
    public int getVidaNova() { return vidaNova; }
}
