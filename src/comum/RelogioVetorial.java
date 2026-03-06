package comum;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RelogioVetorial implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String processoId;
    private Map<String, Integer> vetor;

    public RelogioVetorial(String processoId) {
        this.processoId = processoId;
        this.vetor = new ConcurrentHashMap<>();
        this.vetor.put(processoId, 0);
    }

    public synchronized void tick() {
        vetor.put(processoId, vetor.get(processoId) + 1);
    }

    public synchronized void atualizar(Map<String, Integer> vetorRecebido) {
        for (Map.Entry<String, Integer> entry : vetorRecebido.entrySet()) {
            String processo = entry.getKey();
            int tempo = entry.getValue();
            vetor.put(processo, Math.max(vetor.getOrDefault(processo, 0), tempo));
        } // Atualiza com máximo de cada processo
        vetor.put(processoId, vetor.get(processoId) + 1);
    }

    public synchronized void adicionarProcesso(String novoProcesso) {
        vetor.putIfAbsent(novoProcesso, 0);
    }

    public synchronized Map<String, Integer> getVetor() {
        return new HashMap<>(vetor);
    }

    public static RelacaoCausal comparar(Map<String, Integer> v1, Map<String, Integer> v2) {
        boolean v1MenorIgual = true;
        boolean v2MenorIgual = true;
        boolean algumMenor = false;

        Set<String> todosProcessos = new HashSet<>();
        todosProcessos.addAll(v1.keySet());
        todosProcessos.addAll(v2.keySet());

        for (String processo : todosProcessos) {
            int tempo1 = v1.getOrDefault(processo, 0);
            int tempo2 = v2.getOrDefault(processo, 0);

            if (tempo1 < tempo2) {
                v2MenorIgual = false;
                algumMenor = true;
            } else if (tempo1 > tempo2) {
                v1MenorIgual = false;
                algumMenor = true;
            }
        }

        if (v1MenorIgual && algumMenor) return RelacaoCausal.ANTES;
        if (v2MenorIgual && algumMenor) return RelacaoCausal.DEPOIS;
        return RelacaoCausal.CONCORRENTE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("V[");
        List<String> keys = new ArrayList<>(vetor.keySet());
        Collections.sort(keys);

        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            sb.append(k.substring(0, Math.min(8, k.length()))).append(":")
                    .append(vetor.get(k));
            if (i < keys.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public enum RelacaoCausal {
        ANTES,        // v1 → v2 (v1 causou v2)
        DEPOIS,       // v2 → v1 (v2 causou v1)
        CONCORRENTE   // v1 || v2 (sem relação causal)
    }
}