package bases;

import comum.EstadoUniverso;
import comum.Entidade;
import comum.TipoEntidade;
import comum.mensagens.comandos.ComandoAtacar;
import comum.mensagens.comandos.ComandoMovimento;
import comum.mensagens.comandos.Direcao;
import java.util.*;
import java.util.concurrent.*;

public class GerenciadorIABase {
    private final ServidorBase base;
    private ScheduledExecutorService scheduler;
    private boolean ativo;

    public GerenciadorIABase(ServidorBase base) {
        this.base = base;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void iniciar() {
        if (ativo) return;
        ativo = true;
        scheduler.scheduleAtFixedRate(this::moverNavesIA, 5, 10, TimeUnit.SECONDS);
    }

    private void moverNavesIA() {
        EstadoUniverso estado = base.getUltimoEstado();
        if (estado == null) return;

        List<Entidade> navesIA = estado.getNaves().values().stream()
                .filter(n -> n.getId().startsWith("Imperial_AI_"))
                .toList();

        for (Entidade nave : navesIA) {
            Entidade alvo = encontrarAlvoMaisProximo(nave, estado);

            if (alvo != null) {
                double dist = distancia(nave, alvo);

                if (dist <= 25) {// está perto o suficiente — ataca
                    int dx = alvo.getX() - nave.getX();
                    int dy = alvo.getY() - nave.getY();
                    // normaliza para 1 casa
                    int xAlvo = nave.getX() + (dx == 0 ? 0 : dx / Math.abs(dx));
                    int yAlvo = nave.getY() + (dy == 0 ? 0 : dy / Math.abs(dy));
                    System.out.printf("[IA] %s atacando %s distancia:%.1f alvo:(%d,%d)%n",
                            nave.getId(), alvo.getId(), dist, xAlvo, yAlvo);
                    ComandoAtacar cmd = new ComandoAtacar(nave.getId(), xAlvo, yAlvo);
                    base.enviarParaGalaxia(cmd);
                } else {
                    String direcao = calcularDirecao(nave, alvo);// move em direção ao alvo
                    ComandoMovimento cmd = new ComandoMovimento(
                            nave.getId(), Direcao.valueOf(direcao));
                    base.enviarParaGalaxia(cmd);
                }
            } else {
                String direcao = direcaoAleatoria();// sem alvo, move aleatoriamente
                ComandoMovimento cmd = new ComandoMovimento(
                        nave.getId(), Direcao.valueOf(direcao));
                base.enviarParaGalaxia(cmd);
            }
        }
    }

    private Entidade encontrarAlvoMaisProximo(Entidade naveIA, EstadoUniverso estado) {
        return estado.getNaves().values().stream()
                .filter(n -> n.getTipo() == TipoEntidade.NAVE_REBELDE)
                .min(Comparator.comparingDouble(n -> distancia(naveIA, n)))
                .orElse(null);
    }

    private String calcularDirecao(Entidade origem, Entidade alvo) {
        int dx = alvo.getX() - origem.getX();
        int dy = alvo.getY() - origem.getY();
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? "DIREITA" : "ESQUERDA";
        return dy > 0 ? "BAIXO" : "CIMA";
    }

    private String direcaoAleatoria() {
        String[] dirs = {"CIMA", "BAIXO", "ESQUERDA", "DIREITA"};
        return dirs[new Random().nextInt(dirs.length)];
    }

    private double distancia(Entidade a, Entidade b) {
        int dx = a.getX() - b.getX(), dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public void parar() { ativo = false; scheduler.shutdown(); }
}