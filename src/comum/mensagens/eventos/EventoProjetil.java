package comum.mensagens.eventos;

public class EventoProjetil extends Evento {
    private final int x;
    private final int y;
    private final String atacanteId;
    private final boolean ativo; // false = animação terminou

    public EventoProjetil(String remetente, int x, int y,
                          String atacanteId, boolean ativo) {
        super(remetente);
        this.x = x;
        this.y = y;
        this.atacanteId = atacanteId;
        this.ativo = ativo;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public String getAtacanteId() { return atacanteId; }
    public boolean isAtivo() { return ativo; }
}