package bases;

import comum.Entidade;
import comum.TipoEntidade;
import comum.mensagens.eventos.*;
import java.util.*;
import java.util.concurrent.*;

public class GerenciadorSpawnBase {
    private final ServidorBase base;
    private ScheduledExecutorService scheduler;
    private boolean ativo;
    private int ondasCriadas;

    public GerenciadorSpawnBase(ServidorBase base) {
        this.base = base;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void iniciar() {
        if (ativo) return;
        ativo = true;
        scheduler.schedule(() -> {
            criarNavesImperiais(2);
            ondasCriadas++;
            scheduler.scheduleAtFixedRate(() -> {
                if (ativo) { criarNavesImperiais(1); ondasCriadas++; }
            }, 10, 60, TimeUnit.SECONDS);
        }, 10, TimeUnit.SECONDS);
    }

    private void criarNavesImperiais(int quantidade) {
        List<String> navesIds = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < quantidade; i++) {
            String naveId = "Imperial_AI_" + base.proximoNumeroNave();
            int x = 40 + rand.nextInt(5) - 2;// posição perto do Star Destroyer
            int y = 7 + rand.nextInt(5) - 2;

            Entidade nave = new Entidade(naveId, TipoEntidade.NAVE_IMPERIAL, x, y, 100, 100);
            base.adicionarNaveIA(nave);
            navesIds.add(naveId);
            System.out.println("[SPAWN] Nave imperial criada: " + naveId);
        }

        String msg = String.format("%d nave%s imperial%s surgiu nas proximidades!",
                quantidade, quantidade > 1 ? "s" : "", quantidade > 1 ? "is" : "");

        base.broadcastGalaxia(new EventoChatRecebido("SISTEMA", msg));
        base.broadcastGalaxia(new EventoNavesInimigasCriadas(
                "SPAWN_SYSTEM", quantidade, "Estrela da Morte", navesIds));
    }

    public void parar() { ativo = false; scheduler.shutdown(); }
}