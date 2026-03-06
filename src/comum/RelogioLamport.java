package comum;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class RelogioLamport implements Serializable {
    private static final long serialVersionUID = 1L;
    private AtomicInteger tempo;

    public RelogioLamport() {
        this.tempo = new AtomicInteger(0);
    }

    public int tick() {return tempo.incrementAndGet();}

    public int atualizar(int tempoRecebido) {
        int novoTempo;
        int atual;
        do {
            atual = tempo.get();
            novoTempo = Math.max(atual, tempoRecebido) + 1;
        } while (!tempo.compareAndSet(atual, novoTempo));

        return novoTempo;
    }

    public int getTempo() {return tempo.get();}

    public void setTempo(int novoTempo) {tempo.set(novoTempo);}

    public static boolean aconteceuAntes(int t1, int t2) {
        return t1 < t2;
    }

    @Override
    public String toString() {
        return "L" + tempo.get();
    }
}