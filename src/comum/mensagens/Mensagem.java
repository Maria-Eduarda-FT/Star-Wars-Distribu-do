package comum.mensagens;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

public abstract class Mensagem implements Serializable {

    protected final String remetente;
    protected final LocalDateTime timestamp;
    protected int timestampLamport;
    protected Map<String, Integer> timestampVetorial;

    protected Mensagem(String remetente) {
        this.remetente = remetente;
        this.timestamp = LocalDateTime.now();
        this.timestampLamport = 0;
        this.timestampVetorial = null;
    }

    public String getRemetente() {return remetente;}
    public int getTimestampLamport() { return timestampLamport; }
    public Map<String, Integer> getTimestampVetorial() { return timestampVetorial; }

    public void setTimestampLamport(int tempo) {this.timestampLamport = tempo;}
    public void setTimestampVetorial(Map<String, Integer> vetor) {this.timestampVetorial = vetor;}

    @Override
    public String toString() {
        return String.format("[%s|L%d] de %s",
                getClass().getSimpleName(),
                timestampLamport,
                remetente);
    }

}
