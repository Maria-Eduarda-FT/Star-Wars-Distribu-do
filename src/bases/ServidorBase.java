package bases;

import comum.*;
import comum.mensagens.*;
import comum.mensagens.comandos.*;
import comum.mensagens.eventos.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorBase {
    private final String baseId;
    private final TipoEntidade tipoBase;
    private final int porta;
    private final int capacidadeSimultanea;
    private final int recursosMaximos;

    private ServerSocket serverSocket;
    private boolean rodando;
    private ExecutorService threadPool;
    private RelogioLamport relogioLamport;
    private RelogioVetorial relogioVetorial;

    private int recursosDisponiveis;
    private Map<String, ConexaoNave> navesReabastecendo;
    private BlockingQueue<SolicitacaoReabastecimento> filaEspera;

    private Socket socketGalaxia;
    private ObjectOutputStream outGalaxia;
    private ObjectInputStream inGalaxia;

    private volatile EstadoUniverso ultimoEstado;
    private GerenciadorSpawnBase gerenciadorSpawn;
    private GerenciadorIABase gerenciadorIA;

    private final AtomicInteger contadorNavesIA = new AtomicInteger(0);

    public ServidorBase(String baseId, TipoEntidade tipoBase, int porta,
                        int capacidade, int recursos) {
        this.baseId = baseId;
        this.tipoBase = tipoBase;
        this.porta = porta;
        this.capacidadeSimultanea = capacidade;
        this.recursosMaximos = recursos;
        this.recursosDisponiveis = recursos;
        this.rodando = true;
        this.threadPool = Executors.newCachedThreadPool();
        this.relogioLamport = new RelogioLamport();
        this.relogioVetorial = new RelogioVetorial(baseId);
        this.navesReabastecendo = new ConcurrentHashMap<>();
        this.filaEspera = new LinkedBlockingQueue<>();
    }

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);

            String simbolo = switch (tipoBase) {
                case BASE_REBELDE -> "⊚";
                case STAR_DESTROYER -> "⨹";
                case PLANETA -> "Ø";
                default -> "?";
            };

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║  " + simbolo + " SERVIDOR BASE: " + baseId);
            System.out.println("║  Porta: " + porta);
            System.out.println("║  Capacidade: " + capacidadeSimultanea + " naves");
            System.out.println("║  Recursos: " + recursosDisponiveis + "/" + recursosMaximos);
            System.out.println("╚════════════════════════════════════════╝");

            conectarComGalaxia();
            threadPool.execute(this::processarFilaReabastecimento);
            if (tipoBase == TipoEntidade.STAR_DESTROYER) {
                gerenciadorSpawn = new GerenciadorSpawnBase(this);
                gerenciadorIA = new GerenciadorIABase(this);
                gerenciadorSpawn.iniciar();
                gerenciadorIA.iniciar();
            }

            while (rodando) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[" + baseId + "] Nova conexão: " +
                            clientSocket.getInetAddress());

                    threadPool.execute(() -> atenderNave(clientSocket));

                } catch (IOException e) {
                    if (rodando) {
                        System.err.println("[" + baseId + "] Erro ao aceitar: " +
                                e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao iniciar: " + e.getMessage());
        }
    }

    private void conectarComGalaxia() {
        try {
            socketGalaxia = new Socket("localhost", 5555);
            outGalaxia = new ObjectOutputStream(socketGalaxia.getOutputStream());
            inGalaxia = new ObjectInputStream(socketGalaxia.getInputStream());

            ComandoRegistrarBase cmd = new ComandoRegistrarBase(baseId, tipoBase, porta);
            outGalaxia.writeObject(cmd);
            outGalaxia.flush();

            threadPool.execute(this::receberMensagensGalaxia);
            System.out.println("[" + baseId + "] Conectado com servidor Galáxia");

        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao conectar com Galáxia: " +
                    e.getMessage());
        }
    }

    private void receberMensagensGalaxia() {
        try {
            while (rodando) {
                Object msg = inGalaxia.readObject();
                if (msg instanceof EventoEstadoUniverso evento) {
                    // Star Destroyer usa para atualizar estado local das naves
                    processarEstadoUniverso(evento.getEstado());
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (rodando) System.err.println("[" + baseId + "] Conexão com Galáxia perdida");
        }
    }

    private void processarEstadoUniverso(EstadoUniverso estado) {
        if (tipoBase == TipoEntidade.STAR_DESTROYER) {
            ultimoEstado = estado;
        }
    }

    public void adicionarNaveIA(Entidade nave) {
        try {// notifica Galáxia para adicionar a nave
            outGalaxia.writeObject(new ComandoRegistrarNaveIA(nave));
            outGalaxia.flush();
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao registrar nave IA: " + e.getMessage());
        }
    }

    public void enviarParaGalaxia(Mensagem msg) {
        try {
            outGalaxia.writeObject(msg);
            outGalaxia.flush();
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao enviar para Galáxia: " + e.getMessage());
        }
    }

    public void broadcastGalaxia(Mensagem msg) {
        enviarParaGalaxia(msg);
    }

    public EstadoUniverso getUltimoEstado() { return ultimoEstado; }

    private void atenderNave(Socket socket) {
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Object obj = in.readObject();// solicitação

            if (obj instanceof ComandoReabastecer cmd) {
                String naveId = cmd.getRemetente();
                TipoEntidade tipoNave = determinarTipoNave(naveId);

                relogioLamport.atualizar(cmd.getTimestampLamport());
                if (cmd.getTimestampVetorial() != null) {
                    relogioVetorial.atualizar(cmd.getTimestampVetorial());
                }
                System.out.printf("[%s] Solicitação de %s (L%d)%n",
                        baseId, naveId, cmd.getTimestampLamport());
                if (!podeAceitarNave(tipoNave)) {
                    enviarRecusa(out, naveId, "Base não atende sua facção!");
                    socket.close();
                    return;
                }
                if (recursosDisponiveis < 20) {
                    enviarRecusa(out, naveId, "Recursos insuficientes!");
                    socket.close();
                    return;
                }
                SolicitacaoReabastecimento solicitacao = new SolicitacaoReabastecimento(
                        naveId, socket, out, in, cmd.getTimestampLamport()
                ); filaEspera.offer(solicitacao);
                int posicao = filaEspera.size();
                enviarMensagemFila(out, naveId, posicao);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[" + baseId + "] Erro ao atender nave: " +
                    e.getMessage());
        }
    }

    private void processarFilaReabastecimento() {
        while (rodando) {
            try {
                if (navesReabastecendo.size() >= capacidadeSimultanea) {
                    Thread.sleep(500);continue;
                }

                SolicitacaoReabastecimento solicitacao = filaEspera.poll(1, TimeUnit.SECONDS);
                if (solicitacao == null) continue;
                processarReabastecimento(solicitacao);
            } catch (InterruptedException e) {break;}
        }
    }

    private void processarReabastecimento(SolicitacaoReabastecimento solicitacao) {
        String naveId = solicitacao.naveId;

        try {
            ConexaoNave conexao = new ConexaoNave(solicitacao.socket, solicitacao.out, solicitacao.in);
            navesReabastecendo.put(naveId, conexao);
            System.out.printf("[%s]  Reabastecendo %s (L%d)...%n",
                    baseId, naveId, solicitacao.timestampLamport);

            Thread.sleep(2000);
            recursosDisponiveis -= 20;
            int tempo = relogioLamport.tick();
            relogioVetorial.tick();

            EventoReabastecimento evento = new EventoReabastecimento(
                    baseId, naveId, true,
                    "Reabastecimento completo em " + baseId,
                    100, 100
            );
            evento.setTimestampLamport(tempo);
            evento.setTimestampVetorial(relogioVetorial.getVetor());
            solicitacao.out.writeObject(evento);
            solicitacao.out.flush();
            System.out.printf("[%s] %s reabastecido! Recursos: %d/%d%n",
                    baseId, naveId, recursosDisponiveis, recursosMaximos);

            notificarGalaxia(naveId);
            navesReabastecendo.remove(naveId);
            solicitacao.socket.close();

        } catch (IOException | InterruptedException e) {
            System.err.println("[" + baseId + "] Erro no reabastecimento: " +
                    e.getMessage());
            navesReabastecendo.remove(naveId);
        }
    }

    private boolean podeAceitarNave(TipoEntidade tipoNave) {
        return switch (tipoBase) {
            case BASE_REBELDE -> tipoNave == TipoEntidade.NAVE_REBELDE;
            case STAR_DESTROYER -> tipoNave == TipoEntidade.NAVE_IMPERIAL;
            case PLANETA -> true; // Aceita todos
            default -> false;
        };
    }

    private TipoEntidade determinarTipoNave(String naveId) {
        if (naveId.startsWith("Rebelde")) return TipoEntidade.NAVE_REBELDE;
        if (naveId.startsWith("Imperial")) return TipoEntidade.NAVE_IMPERIAL;
        return TipoEntidade.NAVE_REBELDE;
    }

    private void enviarRecusa(ObjectOutputStream out, String naveId, String motivo) {
        try {
            int tempo = relogioLamport.tick();
            EventoReabastecimento evento = new EventoReabastecimento(
                    baseId, naveId, false, motivo, 0, 0
            );
            evento.setTimestampLamport(tempo);
            out.writeObject(evento);out.flush();

            System.out.printf("[%s]  Recusou %s: %s%n", baseId, naveId, motivo);
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao enviar recusa: " +
                    e.getMessage());
        }
    }

    private void enviarMensagemFila(ObjectOutputStream out, String naveId, int posicao) {
        try {
            EventoChatRecebido msg = new EventoChatRecebido(baseId,
                    "Posição na fila: " + posicao + " | Aguarde...");
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao enviar msg fila: " +
                    e.getMessage());
        }
    }

    private void notificarGalaxia(String naveId) {
        try {
            EventoReabastecimentoConcluido evento = new EventoReabastecimentoConcluido(
                    baseId, naveId, baseId, 100, 100
            );
            outGalaxia.writeObject(evento);
            outGalaxia.flush();
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao notificar Galáxia: " +
                    e.getMessage());
        }
    }

    public int proximoNumeroNave() {
        return contadorNavesIA.incrementAndGet();
    }

    public void encerrar() {
        rodando = false;
        threadPool.shutdown();
        try {
            if (serverSocket != null) serverSocket.close();
            if (socketGalaxia != null) socketGalaxia.close();
        } catch (IOException e) {
            System.err.println("[" + baseId + "] Erro ao encerrar: " + e.getMessage());
        }
    }

    private static class SolicitacaoReabastecimento {
        String naveId;
        Socket socket;
        ObjectOutputStream out;
        ObjectInputStream in;
        int timestampLamport;

        SolicitacaoReabastecimento(String naveId, Socket socket,
                                   ObjectOutputStream out, ObjectInputStream in,
                                   int timestamp) {
            this.naveId = naveId;this.socket = socket;
            this.out = out;this.in = in;this.timestampLamport = timestamp;
        }
    }

    private static class ConexaoNave {
        Socket socket;
        ObjectOutputStream out;
        ObjectInputStream in;

        ConexaoNave(Socket socket, ObjectOutputStream out, ObjectInputStream in) {
            this.socket = socket;this.out = out;this.in = in;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java ServidorBase <tipo>");
            System.out.println("Tipos: rebelde, imperial, planeta");
            return;
        }

        String tipo = args[0].toLowerCase();
        ServidorBase base = switch (tipo) {
            case "rebelde" -> new ServidorBase(
                    "base_rebelde_1", TipoEntidade.BASE_REBELDE, 5556, 2, 1000
            );
            case "imperial" -> new ServidorBase(
                    "star_destroyer_1", TipoEntidade.STAR_DESTROYER, 5557, 3, 2000
            );
            case "planeta" -> new ServidorBase(
                    "planeta_neutro", TipoEntidade.PLANETA, 5558, 1, 500
            );
            default -> null;
        };
        if (base != null) {base.iniciar();}
    }
}