package comum.mensagens.eventos;

public class EventoDanoRecebido extends Evento {
    private final String naveId;
    private final int dano;
    private final String fonte; // "asteroide", "nave_imperial", etc
    private final int vidaRestante;

    public EventoDanoRecebido(String remetente, String naveId, int dano,
                              String fonte, int vidaRestante) {
        super(remetente);
        this.naveId = naveId; this.dano = dano;
        this.fonte = fonte; this.vidaRestante = vidaRestante;
    }

    public String getNaveId() { return naveId; }
    public int getDano() { return dano; }
    public String getFonte() { return fonte; }
    public int getVidaRestante() { return vidaRestante; }
}