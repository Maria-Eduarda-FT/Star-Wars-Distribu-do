package servidor;
import comum.*;

import comum.mensagens.Mensagem;
import comum.mensagens.eventos.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Galaxia {
    private static final int PORTA = 5555;
    private static final int mapaLargura = 50;
    private static final int mapaAltura = 15;

    private ServicoDescoberta servicoDescoberta;

    private RelogioLamport relogioLamport;
    private RelogioVetorial relogioVetorial;

    private Map<String, Entidade> naves;
    private Map<String, Entidade> bases;
    private Map<String, Integer> portasBases;
    private Map<String, GerenciadorConexoes> conexoes;
    private ServerSocket serverSocket;
    private boolean rodando;
    private ExecutorService threadPool;
    private final AtomicInteger contadorNaves = new AtomicInteger(0);

    public Galaxia() {
        this.naves = new ConcurrentHashMap<>();
        this.bases = new ConcurrentHashMap<>();
        this.portasBases = new ConcurrentHashMap<>();
        this.conexoes = new ConcurrentHashMap<>();
        this.rodando = true;
        this.threadPool = Executors.newCachedThreadPool();
        this.relogioLamport = new RelogioLamport();
        this.relogioVetorial = new RelogioVetorial("SERVIDOR_GALAXIA");

        inicializarBases();

        this.servicoDescoberta = new ServicoDescoberta();
        this.servicoDescoberta.escutarServidores();
    }

    public Map<String, Entidade> getNaves() {return naves;}
    public Map<String, Entidade> getBases() {return bases;}
    public int getMapaLargura() {return mapaLargura;}
    public int getMapaAltura() {return mapaAltura;}

    private void inicializarBases() {
        int x = new Random().nextInt(mapaLargura);
        int y = new Random().nextInt(mapaAltura);
        Entidade baseRebelde = new Entidade(
                "base_rebelde_1", TipoEntidade.BASE_REBELDE,
                x, y, 100, 5000
        );
        bases.put("base_rebelde_1", baseRebelde);
        x = new Random().nextInt(mapaLargura);
        y = new Random().nextInt(mapaAltura);
        Entidade starDestroyer = new Entidade(
                "star_destroyer_1", TipoEntidade.STAR_DESTROYER,
                x, y, 100, 5000
        );
        bases.put("star_destroyer_1", starDestroyer);
        x = new Random().nextInt(mapaLargura);
        y = new Random().nextInt(mapaAltura);
        Entidade planetaNeutro = new Entidade(
                "planeta_neutro", TipoEntidade.PLANETA,
                x, y, 100, 3000
        );
        bases.put("planeta_neutro", planetaNeutro);
    }

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(PORTA);
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║  SERVIDOR GALÁXIA STAR WARS ATIVO     ║");
            System.out.println("║  Porta: " + PORTA + "                          ║");
            System.out.println("╚════════════════════════════════════════╝");

            servicoDescoberta.anunciarServidor("GALAXIA", "Galáxia Principal", PORTA);
            threadPool.execute(this::loopAtualizacao);

            while (rodando) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[SERVIDOR] Nova conexão: " + clientSocket.getInetAddress());
                    GerenciadorConexoes gerenciador = new GerenciadorConexoes(clientSocket, this);
                    threadPool.execute(gerenciador);
                } catch (IOException e) {
                    if (rodando)
                        System.err.println("[SERVIDOR] Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVIDOR] Erro ao iniciar servidor: " + e.getMessage());
        }
    }

    public void adicionarConexao(String id, GerenciadorConexoes gerenciador) {
        conexoes.put(id, gerenciador);
    }

    public synchronized Entidade registrarNave(String naveId, String faccao, GerenciadorConexoes gerenciador) {
        int x, y;
        int numero = contadorNaves.incrementAndGet();
        String nomeNave = (faccao.equals("rebelde") ? "Rebelde_" : "Imperial_") + numero;

        if (faccao.equals("rebelde")) {
            x = 3 + new Random().nextInt(8);
            y = 3 + new Random().nextInt(6);
        } else {
            x = 10 + new Random().nextInt(8);
            y = 7 + new Random().nextInt(6);
        }

        TipoEntidade tipo = faccao.equals("rebelde") ?
                TipoEntidade.NAVE_REBELDE : TipoEntidade.NAVE_IMPERIAL;

        Entidade nave = new Entidade(nomeNave, tipo, x, y, 100, 100);
        naves.put(nomeNave, nave);
        conexoes.put(nomeNave, gerenciador);
        broadcast(new EventoNaveEntrou(nomeNave, nave));
        return nave;
    }

    public synchronized long contarNavesRebeldes(String excluirId) {
        return naves.values().stream()
                .filter(n -> n.getTipo() == TipoEntidade.NAVE_REBELDE
                        && !n.getId().equals(excluirId)
                        && !n.getId().startsWith("Imperial_AI_"))
                .count();
    }

    public synchronized void broadcastExceto(Mensagem msg, String excluirId) {
        for (Map.Entry<String, GerenciadorConexoes> entry : conexoes.entrySet()) {
            if (!entry.getKey().equals(excluirId) && naves.containsKey(entry.getKey())) {
                entry.getValue().enviarMensagem(msg);
            }
        }
    }

    public synchronized void registrarBase(String baseId, TipoEntidade tipo, int porta) {
        portasBases.put(baseId, porta);
        System.out.printf("[GALÁXIA] Base registrada: %s tipo=%s porta=%d%n",
                baseId, tipo, porta);
    }

    public Integer getPortaBase(String baseId) {return portasBases.get(baseId);}

    public synchronized boolean moverNave(String naveId, String direcao) {
        Entidade nave = naves.get(naveId);
        if (nave == null) {
            System.out.println("[SERVIDOR] ERRO: Nave " + naveId + " não encontrada!");
            return false;
        }
        int novoX = nave.getX();
        int novoY = nave.getY();

        switch (direcao.toUpperCase()) {
            case "CIMA": novoY--; break;
            case "BAIXO": novoY++; break;
            case "ESQUERDA": novoX--; break;
            case "DIREITA": novoX++; break;
            default:
                System.out.println("[SERVIDOR] ERRO: Direção inválida: " + direcao);
                return false;
        }
        if (novoX < 0 || novoX >= mapaLargura || novoY < 0 || novoY >= mapaAltura) {
            return false;
        }

        nave.setCombustivel(nave.getCombustivel() - 1);
        nave.setX(novoX); nave.setY(novoY);

        verificarProximidadeBases(nave);
        verificarProximidadeNaves(nave);
        enviarEstadoParaTodos();
        return true;
    }

    public synchronized void processarAtaque(String atacanteId, int xAtual, int yAtual) {
        Entidade atacante = naves.get(atacanteId);
        if (atacante == null) return;

        int dx = xAtual - atacante.getX(); // calcula direção do tiro baseado na posição do atacante
        int dy = yAtual - atacante.getY();

        // roda em thread separada para transmitir frames sem bloquear o servidor
        final int fdx = dx, fdy = dy;
        threadPool.execute(() -> {
            int x = atacante.getX() + dx;// percorre casa por casa na direção do tiro
            int y = atacante.getY() + dy;

            while (x >= 0 && x < mapaLargura && y >= 0 && y < mapaAltura) {
                broadcast(new EventoProjetil("SERVIDOR", x, y, atacanteId, true));
                try { Thread.sleep(200); }
                catch (InterruptedException e) { return; }
                synchronized (this) {// verifica colisão
                    Entidade alvo = null;
                    for (Entidade nave : naves.values()) {
                        if (nave.getX() == x && nave.getY() == y
                                && !nave.getId().equals(atacanteId)) {
                            alvo = nave; break;
                        }
                    }

                    if (alvo == null) {
                        for (Entidade base : bases.values()) {
                            if (base.getX() == x && base.getY() == y) {
                                if (base.getTipo() == TipoEntidade.STAR_DESTROYER) {
                                    alvo = base;
                                } break;
                            }
                        }
                    }
                    if (alvo != null) {
                        boolean ehBase = alvo.getTipo() == TipoEntidade.STAR_DESTROYER
                                && bases.containsValue(alvo);

                        if (!ehBase && atacante.getTipo() == alvo.getTipo()) {
                            enviarParaNave(atacanteId, new EventoChatRecebido(
                                    "SISTEMA", "Não pode atacar aliados!"));
                            broadcast(new EventoProjetil("SERVIDOR", x, y, atacanteId, false));
                            return;
                        }

                        int dano = 10, vidaNova = alvo.getVida() - dano;
                        alvo.setVida(vidaNova);
                        System.out.printf("[COMBATE] %s atacou %s — dano:%d vida:%d%n",
                                atacanteId, alvo.getId(), dano, vidaNova);

                        enviarParaNave(atacanteId, new EventoChatRecebido(
                                "SISTEMA", "Acertou " + alvo.getId() + "! (-" + dano + " vida)"));

                        if (vidaNova <= 0) {
                            if (ehBase) {
                                broadcast(new EventoVitoria("SERVIDOR",
                                        "Estrela da Morte destruída por " + atacanteId + "!"));
                                bases.remove("star_destroyer_1");
                            } else {
                                enviarParaNave(alvo.getId(), new EventoDerrota("SERVIDOR",
                                        alvo.getId(), alvo.getId() + " foi destruído!"));
                                broadcast(new EventoChatRecebido("SISTEMA",
                                        alvo.getId() + " foi destruído por " + atacanteId + "!"));
                                removerNave(alvo.getId());
                            }
                        } else if (!ehBase) {
                            enviarParaNave(alvo.getId(), new EventoDanoRecebido(
                                    "SERVIDOR", alvo.getId(), dano, atacanteId, vidaNova));
                        }

                        broadcast(new EventoProjetil("SERVIDOR", x, y, atacanteId, false));
                        enviarEstadoParaTodos();
                        return;
                    }
                }
                x += fdx;
                y += fdy;
            }

            broadcast(new EventoProjetil("SERVIDOR", x - fdx, y - fdy, atacanteId, false));
            enviarParaNave(atacanteId, new EventoChatRecebido("SISTEMA", "Tiro no vácuo!"));
            enviarEstadoParaTodos();
        });
    }

    public void enviarEstadoParaTodos() {
        int tempo = relogioLamport.tick();
        relogioVetorial.tick();

        EstadoUniverso estado = gerarEstado();
        EventoEstadoUniverso evento = new EventoEstadoUniverso("SERVIDOR", estado);
        evento.setTimestampLamport(tempo);
        evento.setTimestampVetorial(relogioVetorial.getVetor());

        for (GerenciadorConexoes conn : conexoes.values()) {
            try {conn.enviarMensagem(evento);
            } catch (Exception e) {
                System.err.println("[SERVIDOR] Erro ao enviar estado: " + e.getMessage());
            }
        }
    }

    private void verificarProximidadeBases(Entidade nave) {
        for (Entidade base : bases.values()) {
            double distancia = calcularDistancia(nave, base);
            if (distancia <= 3) {
                GerenciadorConexoes conn = conexoes.get(nave.getId());
                if (conn != null) {
                    conn.enviarMensagem(
                            new EventoProximidadeBases( nave.getId(), base)
                    );
                }
            }
        }
    }

    private void verificarProximidadeNaves(Entidade nave) {
        for (Entidade outraNave : naves.values()) {
            if (outraNave.getId().equals(nave.getId())) continue;
            double distancia = calcularDistancia(nave, outraNave);
            if (distancia <= 2) {
                GerenciadorConexoes conn = conexoes.get(nave.getId());
                if (conn != null) {
                    boolean aliada = nave.getTipo().equals(outraNave.getTipo());
                    conn.enviarMensagem(
                            new EventoProximidadeNaves(
                                    nave.getId(), outraNave,// nave detectada
                                    aliada
                            )
                    );
                }
            }
        }
    }

    public void enviarParaNave(String naveId, Mensagem msg) {
        GerenciadorConexoes conn = conexoes.get(naveId);
        if (conn != null) {
            int tempo = relogioLamport.tick();
            relogioVetorial.tick();
            msg.setTimestampLamport(tempo);
            msg.setTimestampVetorial(relogioVetorial.getVetor());
            conn.enviarMensagem(msg);
        }
    }

    public synchronized void removerNave(String naveId) {
        naves.remove(naveId);
        conexoes.remove(naveId);
        System.out.println("[SERVIDOR] Nave removida: " + naveId);
        broadcast(new EventoNaveRemovida("SERVIDOR", naveId));

        boolean aindaHaRebeldes = naves.values().stream()
                .anyMatch(n -> n.getTipo() == TipoEntidade.NAVE_REBELDE
                        && !n.getId().startsWith("Rebelde_AI_"));

        if (!aindaHaRebeldes && !naves.isEmpty()) {
            broadcast(new EventoDerrota("SERVIDOR", "todos",
                    "Todos os pilotos rebeldes foram destruídos! O Império venceu!"));
        }
    }

    public void broadcast(Mensagem msg) {
        for (GerenciadorConexoes conn : conexoes.values()) {
            conn.enviarMensagem(msg);
        }
    }

    public synchronized EstadoUniverso gerarEstado() {
        return new EstadoUniverso(
                new HashMap<>(naves), new HashMap<>(bases),
                mapaLargura, mapaAltura
        );
    }

    private double calcularDistancia(Entidade e1, Entidade e2) {
        int dx = e1.getX() - e2.getX();
        int dy = e1.getY() - e2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void loopAtualizacao() {
        while (rodando) {
            try {
                Thread.sleep(10000);
                synchronized (this) {
                    System.out.println("\n═══════════════════════════════════════");
                    System.out.println("  Naves: " + naves.size());
                    System.out.println("  Relógio Lamport: " + relogioLamport);
                    System.out.println("  Relógio Vetorial: " + relogioVetorial);
                    System.out.println("  Bases registradas: " + portasBases);
                    System.out.println("═══════════════════════════════════════\n");
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public synchronized void atualizarCombustivel(String naveId, int combustivel, int vida) {
        Entidade nave = naves.get(naveId);
        if (nave != null) {
            nave.setCombustivel(combustivel);
            nave.setVida(vida);
            enviarEstadoParaTodos();
        }
    }

    public synchronized void registrarNaveIA(Entidade nave) {
        naves.put(nave.getId(), nave);
        broadcast(new EventoNavesInimigasCriadas(
                "SPAWN_SYSTEM", 1, "Estrela da Morte",
                List.of(nave.getId())
        ));
        enviarEstadoParaTodos();
    }

    public void encerrar() {
        rodando = false;
        servicoDescoberta.parar();
        threadPool.shutdown();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Galaxia servidor = new Galaxia();
        servidor.iniciar();
    }
}