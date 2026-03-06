package comum.mensagens.comandos;

import comum.TipoEntidade;

public class ComandoRegistrarBase extends Comando {
    private final TipoEntidade tipoBase;
    private final int porta;

    public ComandoRegistrarBase(String baseId, TipoEntidade tipoBase, int porta) {
        super(baseId);
        this.tipoBase = tipoBase;
        this.porta = porta;
    }

    public TipoEntidade getTipoBase() { return tipoBase; }
    public int getPorta() { return porta; }

}