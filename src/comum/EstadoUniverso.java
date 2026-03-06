package comum;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class EstadoUniverso implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Entidade> naves;
    private Map<String, Entidade> bases;
    private int largura = 50;
    private int altura = 15;

    public EstadoUniverso(Map<String, Entidade> naves, Map<String, Entidade> bases,
                          int largura, int altura) {
        this.naves = new HashMap<>();
        for (Map.Entry<String, Entidade> entry : naves.entrySet()) {
            this.naves.put(entry.getKey(), copiarEntidade(entry.getValue()));
        }

        this.bases = new HashMap<>();
        for (Map.Entry<String, Entidade> entry : bases.entrySet()) {
            this.bases.put(entry.getKey(), copiarEntidade(entry.getValue()));
        }
        this.largura = largura;
        this.altura = altura;
    }

    private Entidade copiarEntidade(Entidade original) {
        Entidade copia = new Entidade(
                original.getId(),original.getTipo(),
                original.getX(),original.getY(),
                original.getVida(),original.getCombustivel()
        );
        return copia;
    }
    public Map<String, Entidade> getNaves() { return naves; }
    public Map<String, Entidade> getBases() { return bases; }
    public int getLargura() { return largura; }
    public int getAltura() { return altura; }
}