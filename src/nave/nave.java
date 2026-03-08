package nave;
import com.googlecode.lanterna.screen.Screen;
import comum.*;
import comum.Entidade;
import comum.TipoEntidade;
import comum.mensagens.*;
import comum.mensagens.comandos.*;
import comum.mensagens.eventos.*;
import java.io.*;
import java.net.*;
import java.util.*;
import comum.RelogioLamport;
import comum.RelogioVetorial;
import comum.mensagens.eventos.EventoRespostaReabastecimento;
import comum.mensagens.eventos.EventoAcessoConcedido;

public class nave {
    private String id;
    private final String tipo;
    private Socket socket;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private boolean conectado;
    private Entidade estadoLocal;
    private volatile boolean aguardandoReabastecimento = false;
    private Thread threadRecebimento;
    private String baseReabastecimento;
    private volatile EstadoUniverso ultimoEstadoRecebido;

    private List<String> logEventos;
    private static final String HOST = "127.0.0.1";
    private static final int PORTA = 5555;

    private volatile boolean rodando = true;
    private ServicoDescoberta servicoDescoberta;

    private RelogioLamport relogioLamport;
    private RelogioVetorial relogioVetorial;

    private int meuTimestampRequest = -1;
    private List<String> repliesAdiados = Collections.synchronizedList(new ArrayList<>());

    private int repliesRecebidos = 0;
    private int totalNavesRebeldes = 0;
    public nave(String id, String tipo) {
        this.id = id;
        this.tipo = tipo;
        this.conectado = false;

        this.logEventos = new ArrayList<>();
        this.relogioLamport = new RelogioLamport();
        this.relogioVetorial = new RelogioVetorial(id);
        this.aguardandoReabastecimento = false;
        this.baseReabastecimento = null;

        this.servicoDescoberta = new ServicoDescoberta();
        this.servicoDescoberta.escutarServidores();
    }

    public boolean conectar() {
        try {
            socket = new Socket(HOST, PORTA);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Entidade naveLocal = new Entidade(
                    id,
                    tipo.equals("rebelde") ? TipoEntidade.NAVE_REBELDE : TipoEntidade.NAVE_IMPERIAL,
                    0, 0, 100, 100
            );

            ComandoRegistrar registro = new ComandoRegistrar(id, naveLocal);
            out.writeObject(registro);
            out.flush();
            conectado = true; estadoLocal = naveLocal;

            logEventos.add("Nave " + id + " levantou voo");

            threadRecebimento = new Thread(this::receberMensagens);
            threadRecebimento.setDaemon(true);
            threadRecebimento.start();

            System.out.println("Conexão estabelecida! Aguardando sincronização...");
            return true;

        } catch (Exception e) {
            System.err.println("[" + id + "] Erro ao conectar: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private void receberMensagens() {
        try {
            while (conectado) {
                Object obj = in.readObject();

                if (obj instanceof Mensagem msg) {
                    relogioLamport.atualizar(msg.getTimestampLamport());
                    if (msg.getTimestampVetorial() != null) {
                        relogioVetorial.atualizar(msg.getTimestampVetorial());
                        for (String processo : msg.getTimestampVetorial().keySet()) {
                            relogioVetorial.adicionarProcesso(processo);
                        }
                    }
                }

                if (obj instanceof ComandoReabastecer cmdReabastecer) {
                    if (!cmdReabastecer.getRemetente().equals(id)) {
                        String remetenteId = cmdReabastecer.getRemetente();
                        int tsRemoto = cmdReabastecer.getTimestampLamport();

                        boolean euTenhoPreferencia = aguardandoReabastecimento &&
                                (meuTimestampRequest < tsRemoto ||
                                        (meuTimestampRequest == tsRemoto && id.compareTo(remetenteId) < 0));

                        if (euTenhoPreferencia) {
                            repliesAdiados.add(remetenteId);
                            System.out.printf("[EXCLUSÃO] Adiei resposta para %s (meu L%d < L%d deles)%n",
                                    remetenteId, meuTimestampRequest, tsRemoto);
                        } else {
                            try {
                                int tempo = relogioLamport.tick();
                                relogioVetorial.tick();
                                EventoRespostaReabastecimento resposta =
                                        new EventoRespostaReabastecimento(id, remetenteId, true);
                                resposta.setTimestampLamport(tempo);
                                resposta.setTimestampVetorial(relogioVetorial.getVetor());
                                out.writeObject(resposta);
                                out.flush();
                                System.out.printf("[EXCLUSÃO] Enviei OK para %s (L%d)%n", remetenteId, tempo);
                            } catch (IOException e) {
                                System.err.println("Erro ao responder: " + e.getMessage());
                            }
                        }
                    }

                }
                else if (obj instanceof EventoRespostaReabastecimento resposta) {
                    if (resposta.getDestinatario().equals(id) && aguardandoReabastecimento) {
                        repliesRecebidos++;
                        System.out.printf("[R-A] Reply %d/%d recebido%n", repliesRecebidos, totalNavesRebeldes);
                        if (repliesRecebidos >= totalNavesRebeldes) {
                            entrarSecaoCritica();
                        }
                    }
                }
                else if (obj instanceof EventoEstadoUniverso eventoEstado) {
                    processarEstado(eventoEstado.getEstado());
                }
                else if (obj instanceof EventoChatRecebido chat) {
                    if (chat.getRemetente().equals("SERVIDOR_ID")) {
                        this.id = chat.getTexto();
                        logEventos.add("Nave registrada como: " + this.id);
                    } else if (!chat.getRemetente().equals(id)) {
                        logEventos.add("CHAT " + chat.getRemetente() + ": " + chat.getTexto());
                        if (ultimoEstadoRecebido != null) {
                            limparTela();
                            exibirInterface(ultimoEstadoRecebido);
                        }
                    }

                }
                else if (obj instanceof EventoReabastecimento reabast) {
                    if (reabast.isSucesso()) {
                        logEventos.add("REABASTECIMENTO COMPLETO!");
                        estadoLocal.setCombustivel(reabast.getCombustivelNovo());
                        estadoLocal.setVida(reabast.getVidaNova());
                    } else {
                        logEventos.add("REABASTECIMENTO FALHOU: " + reabast.getMotivo());
                    }

                }
                else if (obj instanceof EventoProximidadeBases proxBase) {
                    logEventos.add("Base próxima: " + proxBase.getBase().getId());

                }
                else if (obj instanceof EventoProximidadeNaves proxNave) {
                    String tipo = proxNave.isAliada() ? "ALIADA" : "INIMIGA";
                    logEventos.add("Nave " + tipo + " detectada: " +
                            proxNave.getOutraNave().getId());

                }
                else if (obj instanceof EventoNavesInimigasCriadas navesInimigas) {
                    String msg = String.format(
                            " %d NAVE%s IMPERIAL%s DETECTADA%s!",
                            navesInimigas.getQuantidade(),
                            navesInimigas.getQuantidade() > 1 ? "S" : "",
                            navesInimigas.getQuantidade() > 1 ? "IS" : "",
                            navesInimigas.getQuantidade() > 1 ? "S" : ""
                    );
                    logEventos.add(msg);

                }
                else if (obj instanceof EventoNaveEntrou entrada) {
                    if (!entrada.getId().equals(id)) {
                        logEventos.add("+ " + entrada.getId().getId() + " entrou na galáxia");
                    }

                }
                else if (obj instanceof EventoEnderecoBase enderecoBase) {
                    logEventos.add("Conectando à base " + enderecoBase.getBaseId() + "...");
                    new Thread(() -> conectarEAbastecer(enderecoBase.getHost(),
                            enderecoBase.getPorta(),
                        enderecoBase.getBaseId())).start();
                }
                else if (obj instanceof EventoNaveRemovida saida) {
                    logEventos.add("- " + saida.getNaveId() + " saiu da galáxia");
                    if (aguardandoReabastecimento && !saida.getNaveId().equals(id)) {
                        totalNavesRebeldes = Math.max(0, totalNavesRebeldes - 1);
                        System.out.printf("[R-A] Nave %s saiu — ajustando total para %d%n",
                                saida.getNaveId(), totalNavesRebeldes);
                        if (repliesRecebidos >= totalNavesRebeldes) {
                            entrarSecaoCritica();
                        }
                    }
                }
                else if (obj instanceof EventoProjetil projetil) {
                    if (ultimoEstadoRecebido != null) {
                        if (projetil.isAtivo()) {
                            exibirInterfaceComProjetil(ultimoEstadoRecebido,
                                    projetil.getX(), projetil.getY());
                        } else {
                            limparTela();
                            exibirInterface(ultimoEstadoRecebido);
                        }
                    }
                }
                else if (obj instanceof EventoDanoRecebido dano) {
                    estadoLocal.setVida(dano.getVidaRestante());
                    logEventos.add("DANO RECEBIDO: -" + dano.getDano() +
                            " de " + dano.getFonte() + " | Vida: " + dano.getVidaRestante());
                }
                else if (obj instanceof EventoDerrota derrota) {
                    if (derrota.getNaveId().equals(id) || derrota.getNaveId().equals("todos")) {
                        mostrarDerrota(derrota.getMensagem());
                    }
                }
                else if (obj instanceof EventoVitoria vitoria) {
                    mostrarVitoria(vitoria.getMensagem());
                }
            }
        } catch (EOFException e) {
            conectado = false;
        } catch (IOException | ClassNotFoundException e) {
            if (conectado) {
                System.err.println("[" + id + "] Erro ao receber mensagem: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void processarEstado(EstadoUniverso estado) {
        Entidade naveAtualizada = estado.getNaves().get(id);
        if (naveAtualizada != null) {
            estadoLocal = naveAtualizada;
        }else {System.out.println("[NAVE] ERRO: Não encontrei minha nave no estado!");}

        this.ultimoEstadoRecebido = estado;
        limparTela();
        exibirInterface(estado);
    }

    private String gerarMapaEstrelado(EstadoUniverso estado, int projetilX, int projetilY) {
        StringBuilder sb = new StringBuilder();
        Random rand = new Random();

        char[][] mapa = new char[estado.getAltura()][estado.getLargura()];
        for (int y = 0; y < estado.getAltura(); y++) {
            for (int x = 0; x < estado.getLargura(); x++) {
                mapa[y][x] = ' ';
            }
        }

        for (Entidade base : estado.getBases().values()) {
            char simbolo;
            switch (base.getTipo()) {
                case BASE_REBELDE: simbolo = '⊚'; break;
                case STAR_DESTROYER: simbolo = '⨹'; break;
                case PLANETA: simbolo = 'Ø'; break;
                default: simbolo = '?';
            }
            if (base.getY() >= 0 && base.getY() < estado.getAltura() &&
                    base.getX() >= 0 && base.getX() < estado.getLargura()) {
                mapa[base.getY()][base.getX()] = simbolo;
            }
        }

        for (Entidade nave : estado.getNaves().values()) {
            char simbolo = (nave.getTipo() == TipoEntidade.NAVE_REBELDE) ? '⋈' : '⩙';
            if (nave.getY() >= 0 && nave.getY() < estado.getAltura() &&
                    nave.getX() >= 0 && nave.getX() < estado.getLargura()) {
                mapa[nave.getY()][nave.getX()] = simbolo;
            }
        }

        if (projetilX >= 0 && projetilX < estado.getLargura() &&
                projetilY >= 0 && projetilY < estado.getAltura()) {
            mapa[projetilY][projetilX] = '·';
        }

        for (int y = 0; y < estado.getAltura(); y++) {
            // monta linha de info (nomes) — linha acima
            char[] linhaInfo = new char[estado.getLargura()];
            Arrays.fill(linhaInfo, ' ');
            boolean temInfo = false;

            for (Entidade nave : estado.getNaves().values()) {
                if (nave.getY() == y) {
                    String barra = gerarBarra(nave.getVida(), 100, 3);
                    for (int i = 0; i < barra.length(); i++) {
                        int pos = nave.getX() + i;
                        if (pos >= 0 && pos < estado.getLargura())
                            linhaInfo[pos] = barra.charAt(i);
                    }
                    temInfo = true;
                }
            }
            for (Entidade base : estado.getBases().values()) {
                if (base.getY() == y) {
                    String barra = gerarBarra(base.getVida(), 100, 3);
                    for (int i = 0; i < barra.length(); i++) {
                        int pos = base.getX() + i;
                        if (pos >= 0 && pos < estado.getLargura())
                            linhaInfo[pos] = barra.charAt(i);
                    }
                    temInfo = true;
                }
            }

            if (temInfo) {
                sb.append("║ ");
                sb.append(new String(linhaInfo));
                sb.append(" ║\n");
            }

            // linha dos símbolos
            sb.append("║ ");
            for (int x = 0; x < estado.getLargura(); x++) {
                sb.append(mapa[y][x]);
            }
            sb.append(" ║\n");

            // linha dos nomes
            char[] linhaNomes = new char[estado.getLargura()];
            Arrays.fill(linhaNomes, ' ');
            boolean temNome = false;

            for (Entidade nave : estado.getNaves().values()) {
                if (nave.getY() == y) {
                    String nome = nave.getId();
                    for (int i = 0; i < nome.length(); i++) {
                        int pos = nave.getX() + i;
                        if (pos >= 0 && pos < estado.getLargura() && linhaNomes[pos] == ' ')
                            linhaNomes[pos] = nome.charAt(i);
                    }
                    temNome = true;
                }
            }
            for (Entidade base : estado.getBases().values()) {
                if (base.getY() == y) {
                    String nome = base.getId();
                    for (int i = 0; i < nome.length(); i++) {
                        int pos = base.getX() + i;
                        if (pos >= 0 && pos < estado.getLargura() && linhaNomes[pos] == ' ')
                            linhaNomes[pos] = nome.charAt(i);
                    }
                    temNome = true;
                }
            }

            if (temNome) {
                sb.append("║ ");
                sb.append(new String(linhaNomes));
                sb.append(" ║\n");
            }
        }
        return sb.toString();
    }

    private String gerarMapaEstrelado(EstadoUniverso estado) {
        return gerarMapaEstrelado(estado, -1, -1);
    }

    private String gerarBarra(int vidaAtual, int vidaMax, int tamanho) {
        int preenchido = (int) Math.round((double) vidaAtual / vidaMax * tamanho);
        preenchido = Math.max(0, Math.min(preenchido, tamanho));
        StringBuilder barra = new StringBuilder();
        for (int i = 0; i < tamanho; i++) {
            barra.append(i < preenchido ? '▪' : '·');
        }
        return barra.toString();
    }

    private void entrarSecaoCritica() {
        logEventos.add("[R-A] Todos replies recebidos — solicitando base...");
        try {
            ComandoSolicitarBase cmd = new ComandoSolicitarBase(id);
            cmd.setTimestampLamport(relogioLamport.tick());
            cmd.setTimestampVetorial(relogioVetorial.getVetor());
            synchronized (out) {
                out.reset();
                out.writeObject(cmd);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Erro ao solicitar base: " + e.getMessage());
            aguardandoReabastecimento = false;
        }
    }

    private void liberarSecaoCritica() {
        meuTimestampRequest = -1;
        logEventos.add("[R-A] Liberando seção crítica — enviando replies adiados");
        for (String naveEsperando : repliesAdiados) {
            try {
                EventoRespostaReabastecimento resposta =
                        new EventoRespostaReabastecimento(id, naveEsperando, true);
                resposta.setTimestampLamport(relogioLamport.tick());
                resposta.setTimestampVetorial(relogioVetorial.getVetor());
                synchronized (out) {
                    out.reset();
                    out.writeObject(resposta);
                    out.flush();
                }
                System.out.printf("[R-A] Reply adiado enviado para %s%n", naveEsperando);
            } catch (IOException e) {
                System.err.println("Erro ao enviar reply adiado: " + e.getMessage());
            }
        }
        repliesAdiados.clear();
    }

    private void exibirInterface(EstadoUniverso estado) {
        String simboloNave = tipo.equals("rebelde") ? "⋈" : "⩙";

        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║ Legenda:");
        System.out.println("║  ⨹ = Base Império  | ⩙ = Nave Império  | ⊚ = Base Rebelde | ⋈ = Nave Rebelde");
        System.out.println("║  Ø = Planeta Neutro | ★ = Estrelas");
        System.out.println("║ Comandos: [w/cima] [s/baixo] [a/esquerda] [d/direita] [k/atirar]");
        System.out.println("║           [r/reabastecer] [c/chat <msg>] [sair]");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();

        System.out.println("╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║  " + simboloNave + " NAVE: " + id );
        System.out.println("║  Vida: " + estadoLocal.getVida() + " | Combustível: " + estadoLocal.getCombustivel());
        System.out.println("╚═════════════════════════════════════════════════════════════╝");

        System.out.print(gerarMapaEstrelado(estado));

        System.out.println("_____________________________________________________________");
        System.out.println("║ LOG DE EVENTOS DA GALÁXIA                                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");

        int inicio = Math.max(0, logEventos.size() - 4);
        for (int i = inicio; i < logEventos.size(); i++) {
            System.out.println("║ > " + logEventos.get(i));
        }
        for (int i = logEventos.size(); i < inicio + 3; i++) {
            System.out.println("║");
        }

        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println(); System.out.print("> ");
    }

    public void solicitarStatus() {
        if (!conectado) return;
        try {
            out.writeObject(new MensagemStatus(id));
            out.flush();
        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao solicitar status: " + e.getMessage());
        }
    }

    public void mover(String direcao) {
        if (!conectado) return;
        try {
            int tempo = relogioLamport.tick();
            relogioVetorial.tick();

            ComandoMovimento cmd = new ComandoMovimento(id, Direcao.valueOf(direcao));
            cmd.setTimestampLamport(tempo);
            cmd.setTimestampVetorial(relogioVetorial.getVetor());

            out.writeObject(cmd);
            out.flush();
        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao enviar movimento: " + e.getMessage());
        }
    }

    public void reabastecer() {
        if (!conectado) return;
        if (aguardandoReabastecimento) {
            logEventos.add("Já aguardando reabastecimento...");
            aguardandoReabastecimento = false;
            return;
        }
        try {
            aguardandoReabastecimento = true;
            repliesRecebidos = 0;
            meuTimestampRequest = relogioLamport.tick();
            relogioVetorial.tick();

            totalNavesRebeldes = (ultimoEstadoRecebido != null)
                    ? (int) ultimoEstadoRecebido.getNaves().values().stream()
                    .filter(n -> n.getTipo() == TipoEntidade.NAVE_REBELDE
                            && !n.getId().equals(id))
                    .count()
                    : 0;

            ComandoReabastecer cmd = new ComandoReabastecer(id);
            cmd.setTimestampLamport(meuTimestampRequest);
            cmd.setTimestampVetorial(relogioVetorial.getVetor());

            synchronized (out) {
                out.reset();
                out.writeObject(cmd);
                out.flush();
            }
            logEventos.add("[R-A] REQUEST enviado (L" + meuTimestampRequest + ") para "
                    + totalNavesRebeldes + " naves");


        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao solicitar reabastecimento: " + e.getMessage());
            aguardandoReabastecimento = false;
        }
    }

    private void conectarEAbastecer(String host, int porta, String baseId) {
        try {
            Socket socketBase = new Socket(host, porta);
            socketBase.setSoTimeout(5000);
            ObjectOutputStream outBase = new ObjectOutputStream(socketBase.getOutputStream());
            outBase.flush();
            ObjectInputStream inBase = new ObjectInputStream(socketBase.getInputStream());
            int tempo = relogioLamport.tick();
            relogioVetorial.tick();

            ComandoReabastecer cmd = new ComandoReabastecer(id);
            cmd.setTimestampLamport(tempo);
            cmd.setTimestampVetorial(relogioVetorial.getVetor());
            outBase.writeObject(cmd);
            outBase.flush();

            Object resposta = null;
            while (true) {
                resposta = inBase.readObject();
                if (resposta instanceof EventoReabastecimento) break;
                if (resposta instanceof EventoChatRecebido chat) {
                    logEventos.add("[BASE] " + chat.getTexto()); // mensagem de fila
                }
            }

            EventoReabastecimento evento = (EventoReabastecimento) resposta;
            if (evento.isSucesso()) {
                estadoLocal.setCombustivel(evento.getCombustivelNovo());
                estadoLocal.setVida(evento.getVidaNova());
                logEventos.add("Reabastecimento completo em " + baseId + "!");
                out.writeObject(new MensagemStatus(id));
                out.flush();
            } else {  logEventos.add("Reabastecimento negado: " + evento.getMotivo());}
            socketBase.close();
            aguardandoReabastecimento = false;
            liberarSecaoCritica();

        } catch (IOException | ClassNotFoundException e) {
            logEventos.add("Erro ao conectar com base: " + e.getMessage());
            aguardandoReabastecimento = false;
        }
    }

    public void enviarChat(String mensagem) {
        if (!conectado) return;
        try {
            out.writeObject(new EventoChatRecebido(id, mensagem));
            out.flush();
            logEventos.add("VOCÊ: " + mensagem);
        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao enviar chat: " + e.getMessage());
        }
    }

    public void desconectar() {
        conectado = false;
        servicoDescoberta.parar();
        try {
            if (out != null) {
                out.writeObject(new MensagemStatus(id));
                out.flush();
            }

            if (threadRecebimento != null) { threadRecebimento.interrupt();}
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao desconectar: " + e.getMessage());
        }
    }

    private void limparTela() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }

    public void iniciar() {
        if (!conectar()) {
            System.err.println("Falha ao conectar ao servidor");
            return;
        }

        try { Thread.sleep(1000); }
        catch (InterruptedException e) { e.printStackTrace(); }

        Scanner scanner = new Scanner(System.in);
        System.out.println("\n[INFO] Digite comandos (ou 'ajuda' para ver lista)");
        System.out.println("[INFO] Use comandos curtos: w, s, a, d, k, r, c <msg>, sair\n");

        while (conectado) {
            System.out.print("> ");
            String comando = scanner.nextLine().trim().toLowerCase();
            if (comando.isEmpty()) continue;

            String[] partes = comando.split(" ", 2);
            String acao = partes[0];

            switch (acao) {
                case "w": case "cima":
                    mover("CIMA");
                    break;
                case "s": case "baixo":
                    mover("BAIXO");
                    break;
                case "a": case "esquerda":
                    mover("ESQUERDA");
                    break;
                case "d": case "direita":
                    mover("DIREITA");
                    break;
                case "k":
                    if (partes.length > 1) {
                        atirar(partes[1].toUpperCase());
                    } else {
                        System.out.println("[ERRO] Use: k <direção> (w/a/s/d)");
                    }
                    break;
                case "r":
                    reabastecer();
                    break;
                case "c": case "chat":
                    if (partes.length > 1) enviarChat(partes[1]);
                    else System.out.println("[ERRO] Use: c <mensagem>");
                    break;
                case "status":
                    solicitarStatus();
                    break;
                case "ajuda":
                    mostrarAjuda();
                    break;
                case "sair":
                    desconectar();
                    break;
                default:
                    System.out.println("[ERRO] Comando desconhecido. Digite 'ajuda'");
            }
        }
        scanner.close();
    }

    private void mostrarAjuda() {
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("COMANDOS DISPONÍVEIS:");
        System.out.println("  w ou cima      - Mover para cima");
        System.out.println("  s ou baixo     - Mover para baixo");
        System.out.println("  a ou esquerda  - Mover para esquerda");
        System.out.println("  d ou direita   - Mover para direita");
        System.out.println("  k ou atirar    - Disparar laser");
        System.out.println("  r ou reabastecer - Reabastecer");
        System.out.println("  c <mensagem>   - Enviar chat");
        System.out.println("  status         - Ver status");
        System.out.println("  sair           - Desconectar");
        System.out.println("═══════════════════════════════════════\n");
    }

    public void atirar(String direcao) {
        if (!conectado) return;
        try {
            int dx = 0, dy = 0;
            switch (direcao) {
                case "W": case "CIMA":     dy = -1; break;
                case "S": case "BAIXO":    dy =  1; break;
                case "A": case "ESQUERDA": dx = -1; break;
                case "D": case "DIREITA":  dx =  1; break;
                default:
                    System.out.println("[ERRO] Direção inválida. Use w/a/s/d");
                    return;
            }

            int xAlvo = estadoLocal.getX() + dx;
            int yAlvo = estadoLocal.getY() + dy;
            ComandoAtacar cmd = new ComandoAtacar(id, xAlvo, yAlvo);
            out.writeObject(cmd);
            out.flush();
            logEventos.add("Laser disparado para " + direcao + "!");

        } catch (IOException e) {
            System.err.println("[" + id + "] Erro ao atirar: " + e.getMessage());
        }
    }

    private void exibirInterfaceComProjetil(EstadoUniverso estado, int px, int py) {
        limparTela();
        String simboloNave = tipo.equals("rebelde") ? "⋈" : "⩙";

        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║ Legenda:");
        System.out.println("║  ⨹ = Base Império  | ⩙ = Nave Império  | ⊚ = Base Rebelde | ⋈ = Nave Rebelde");
        System.out.println("║  Ø = Planeta Neutro | ★ = Estrelas | · = Laser");
        System.out.println("║ Comandos: [w/cima] [s/baixo] [a/esquerda] [d/direita] [k/atirar]");
        System.out.println("║           [r/reabastecer] [c/chat <msg>] [sair]");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");

        System.out.println("╔═════════════════════════════════════════════════════════════╗");
        System.out.println("║  " + simboloNave + " NAVE: " + id);
        System.out.println("║  Vida: " + estadoLocal.getVida() +
                " | Combustível: " + estadoLocal.getCombustivel());
        System.out.println("╚═════════════════════════════════════════════════════════════╝");

        System.out.print(gerarMapaEstrelado(estado, px, py));

        System.out.println("_____________________________________________________________");
        System.out.println("║ LOG DE EVENTOS DA GALÁXIA                                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        int inicio = Math.max(0, logEventos.size() - 4);
        for (int i = inicio; i < logEventos.size(); i++)
            System.out.println("║ > " + logEventos.get(i));
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    public static void main(String[] args) throws Exception {
        mostrarIntroducao();

        String id = "Rebelde_" + System.currentTimeMillis();
        String faccao = "rebelde";

        nave nave = new nave(id, faccao);
        nave.iniciar();
    }

    private static void mostrarIntroducao() {
        limparTelaStatic();
        System.out.println("⠀⠀⠀⠀⣴⣶⣶⣶⣶⣶⣶⣶⣶⣶⡆⣴⣶⣶⣶⡀⠀⢰⣶⣶⣶⣶⣦⡄⠀⠀");
        System.out.println("⠀⠀⠀⠀⢿⣿⣿⡍⠉⢹⣿⣿⠉⠉⢡⣿⣿⠹⣿⣧⠀⢸⣿⣿⣤⣼⣿⠇⠀⠀");
        System.out.println("⣶⣶⣶⣶⣶⣿⣿⡿⠀⢸⣿⣿⠀⠀⣾⣿⣿⣿⣿⣿⡆⢸⣿⡟⢿⣿⣷⣶⣶⣶");
        System.out.println("⣭⣭⡍⢩⣭⣭⡉⣤⣤⡌⢩⣭⣤⣤⡉⠉⢠⣤⣬⣭⣥⣌⡉⠁⢀⣩⣭⣭⣭⣭");
        System.out.println("⠸⣿⣿⣿⣿⣿⣷⣿⡿⠀⣾⣿⢿⣿⣇⠀⢸⣿⣿⣛⣻⣿⣷⠀⢿⣿⣿⡛⠛⠛");
        System.out.println("⠀⢻⣿⣿⡿⣿⣿⣿⠃⣸⣿⣿⣼⣿⣿⡄⢸⣿⣿⣿⣿⣿⣥⣤⣬⣿⣿⣿⠀⠀");
        System.out.println("⠀⠈⠿⠿⠁⠹⠿⠟⠀⠿⠿⠉⠉⠹⠿⠧⠸⠿⠿⠈⠛⠿⠿⠿⠿⠿⠿⠋⠀⠀");
        System.out.println();

        System.out.println("\n════════════════════════════════════════════════════════════════");
        System.out.println("                          HISTÓRIA                              ");
        System.out.println("════════════════════════════════════════════════════════════════\n");
        System.out.println("  A Aliança Rebelde está em sua missão contra o Império Galáctico.");
        System.out.println("  Você é um dos pilotos escolhidos para enfrentar a Estrela da Morte!");
        System.out.println("\n  Nesse jogo baseado em Star Wars, seu objetivo é acabar com o");
        System.out.println("  Império de uma vez por todas! Desvie de meteoros, se defenda e");
        System.out.println("  ataque naves inimigas...");
        System.out.println("\n  Os Meteoros infligem -20 de dano e as naves -10. A cada 3 segundos");
        System.out.println("  o visor atualiza e o combustível diminui. Você deve reabastecer em");
        System.out.println("  uma Base Rebelde ou Planeta Neutro. Leva um turno para abastecer!");
        System.out.println("\n════════════════════════════════════════════════════════════════");
        System.out.println("                         CONTROLES                              ");
        System.out.println("════════════════════════════════════════════════════════════════\n");
        System.out.println("       W ou CIMA     - Mover para cima");
        System.out.println("       S ou BAIXO    - Mover para baixo");
        System.out.println("       A ou ESQUERDA - Mover para esquerda");
        System.out.println("       D ou DIREITA  - Mover para direita");
        System.out.println("       K            - Atirar laser\n");
        System.out.println("       REABASTECER  - Reabastecer em uma base\n");
        System.out.println("════════════════════════════════════════════════════════════════\n");
        System.out.print("Pressione ENTER para iniciar sua missão...");

        try {System.in.read();} catch (IOException e) {}
    }

    private void mostrarVitoria(String mensagem) {
        limparTela();
        System.out.println("⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣀⣤⣴⣶⣶⣶⣶⣤⣤⣀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⠀⠀⠀⠀⣀⣴⣾⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣷⣄⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⠀⠀⣴⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣦⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⣠⣿⣿⣿⣿⣿⡿⠿⠛⠛⠉⠉⠉⠉⠛⠛⠿⢿⣿⣿⣿⣿⣿⣿⣄⠀⠀⠀");
        System.out.println("⠀⣼⣿⣿⣿⡿⠋⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠙⢿⣿⣿⣿⣿⣧⠀⠀");
        System.out.println("⢸⣿⣿⣿⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⣿⣿⣿⣿⡇⠀");
        System.out.println("⣿⣿⣿⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⣿⣿⣿⣿⠀");
        System.out.println("⣿⣿⣿⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⣿⣿⠀");
        System.out.println();

        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("                     ✦ VITÓRIA REBELDE ✦                       ");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  Que a Força esteja com você, piloto.");
        System.out.println();
        System.out.println("  " + mensagem);
        System.out.println();
        System.out.println("  A galáxia está livre! O Império Galáctico foi derrotado e");
        System.out.println("  a paz voltará a reinar entre os planetas. A Aliança Rebelde");
        System.out.println("  nunca desistiu — e hoje, a esperança venceu.");
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("           Encerrando em 5 segundos... Que a Força seja com você");
        System.out.println("════════════════════════════════════════════════════════════════");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        desconectar();
        System.exit(0);
    }

    private void mostrarDerrota(String mensagem) {
        limparTela();
        System.out.println("⠀⠀⠀⠀⠀⠀⠀⠀⣀⣤⣶⣶⣦⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⠀⠀⠀⠀⣠⣾⣿⣿⣿⣿⣿⣿⣷⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⠀⠀⣴⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣦⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⠀⣠⣿⣿⣿⣿⣿⣿⠛⠛⠛⠛⠛⢿⣿⣿⣿⣿⣄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⠀⣼⣿⣿⣿⣿⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⣿⣿⣿⣧⠀⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⢸⣿⣿⣿⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢿⣿⣿⣿⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println("⣿⣿⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢻⣿⣿⠀⠀⠀⠀⠀⠀⠀⠀");
        System.out.println();

        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("                     ✦ GAME OVER ✦                             ");
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  " + mensagem);
        System.out.println();
        System.out.println("  A Aliança Rebelde foi derrotada. O Império Galáctico prevaleceu");
        System.out.println("  e a escuridão voltará a dominar a galáxia...");
        System.out.println();
        System.out.println("  Mas a esperança nunca morre. Talvez na próxima missão,");
        System.out.println("  a Força esteja mais forte com você.");
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════");
        System.out.println("           Encerrando em 5 segundos... Que a Força seja com você");
        System.out.println("════════════════════════════════════════════════════════════════");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        desconectar();
        System.exit(0);
    }

    private static void limparTelaStatic() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }


}
