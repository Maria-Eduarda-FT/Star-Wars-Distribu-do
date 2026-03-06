package servidor;

import comum.*;
import comum.Entidade;
import comum.TipoEntidade;
import comum.mensagens.*;
import comum.mensagens.comandos.*;
import comum.mensagens.eventos.*;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class GerenciadorConexoes implements Runnable {
    private final Socket socket;
    private final Galaxia galaxia;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String naveId;

    public GerenciadorConexoes(Socket socket, Galaxia galaxia) {
        this.socket = socket;
        this.galaxia = galaxia;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Object primeiraMsg = in.readObject();
            if (primeiraMsg instanceof ComandoRegistrar registro) {
                String faccao = registro.getNave().getTipo() == TipoEntidade.NAVE_REBELDE
                        ? "rebelde" : "imperial";

                Entidade naveConfirmada = galaxia.registrarNave(
                        registro.getNave().getId(), faccao, this);

                naveId = naveConfirmada.getId();

                enviarMensagem(new EventoChatRecebido("SERVIDOR_ID", naveConfirmada.getId()));
                enviarMensagem(new EventoEstadoUniverso("SERVIDOR", galaxia.gerarEstado()));
                galaxia.broadcast(new EventoNaveEntrou("SERVIDOR", naveConfirmada));
                enviarEstado(galaxia.gerarEstado());
            } else if (primeiraMsg instanceof ComandoRegistrarBase registro) {
                galaxia.registrarBase(registro.getRemetente(),
                        registro.getTipoBase(), registro.getPorta()
                );
                galaxia.adicionarConexao(registro.getRemetente(), this);
                while (true) {
                    Object msg = in.readObject();
                    if (msg instanceof EventoReabastecimentoConcluido concluido) {
                        galaxia.atualizarCombustivel(
                                concluido.getNaveId(),
                                concluido.getCombustivelNovo(),
                                concluido.getVidaNova()
                        );
                    } else if (msg instanceof ComandoRegistrarNaveIA cmd) {
                        galaxia.registrarNaveIA(cmd.getNave());
                    } else if (msg instanceof ComandoMovimento cmd) {
                        galaxia.moverNave(cmd.getRemetente(), cmd.getDirecao().name());
                    } else if (msg instanceof ComandoAtacar cmd) {
                        galaxia.processarAtaque(cmd.getRemetente(), cmd.getXAlvo(), cmd.getYAlvo());
                    } else if (msg instanceof EventoChatRecebido chat) {
                        galaxia.broadcast(chat);
                    }
                }
            }

            while (true) {
                Object mensagem = in.readObject();
                processarMensagem(mensagem);
            }

        } catch (EOFException e) {
            System.out.println("[CONEXÃO] Cliente desconectou: " + naveId);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[CONEXÃO] Erro: " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    private void processarMensagem(Object mensagem) {
        if (mensagem instanceof ComandoMovimento cmd) {
            boolean sucesso = galaxia.moverNave(cmd.getRemetente(), cmd.getDirecao().name());
            if (!sucesso) {
                galaxia.enviarParaNave(cmd.getRemetente(),
                        new EventoChatRecebido("SISTEMA", "Movimento inválido! Limite do mapa."));
            }
        } else if (mensagem instanceof ComandoReabastecer cmd) {
            galaxia.broadcastExceto(cmd, cmd.getRemetente());

            long outrasNaves = galaxia.contarNavesRebeldes(cmd.getRemetente());
            if (outrasNaves == 0) {
                String baseId = encontrarBaseMaisProxima(cmd.getRemetente());
                if (baseId != null) {
                    Integer porta = galaxia.getPortaBase(baseId);
                    galaxia.enviarParaNave(cmd.getRemetente(),
                            new EventoEnderecoBase("SERVIDOR", baseId, "localhost", porta));
                } else {
                    galaxia.enviarParaNave(cmd.getRemetente(),
                            new EventoChatRecebido("SISTEMA", "Nenhuma base próxima!"));
                }
            }

        } else if (mensagem instanceof ComandoSolicitarBase cmd) {
            String baseId = encontrarBaseMaisProxima(cmd.getRemetente());
            if (baseId != null) {
                Integer porta = galaxia.getPortaBase(baseId);
                galaxia.enviarParaNave(cmd.getRemetente(),
                        new EventoEnderecoBase("SERVIDOR", baseId, "localhost", porta));
            } else {
                galaxia.enviarParaNave(cmd.getRemetente(),
                        new EventoChatRecebido("SISTEMA", "Nenhuma base próxima!"));
            }

        } else if (mensagem instanceof EventoRespostaReabastecimento resp) {
            // repassa reply para a nave destinatária — servidor só roteia
            galaxia.enviarParaNave(resp.getDestinatario(), resp);
        } else if (mensagem instanceof ComandoAtacar cmd) {
            System.out.printf("[GERENCIADOR] Ataque de %s → (%d,%d)%n",
                    cmd.getRemetente(), cmd.getXAlvo(), cmd.getYAlvo());
            galaxia.processarAtaque(cmd.getRemetente(), cmd.getXAlvo(), cmd.getYAlvo());
        } else if (mensagem instanceof MensagemStatus) {
            galaxia.enviarEstadoParaTodos();
        } else if (mensagem instanceof EventoChatRecebido chat) {
            galaxia.broadcast(chat);
        }
    }

    private String encontrarBaseMaisProxima(String naveId) {
        Entidade nave = galaxia.getNaves().get(naveId);
        if (nave == null) return null;

        String baseMaisProxima = null;
        double menorDistancia = Double.MAX_VALUE;

        for (Map.Entry<String, Entidade> entry : galaxia.getBases().entrySet()) {
            Entidade base = entry.getValue();
            double distancia = calcularDistancia(nave, base);

            if (distancia <= 3 && distancia < menorDistancia) {
                menorDistancia = distancia;
                baseMaisProxima = entry.getKey();
            }
        }

        return baseMaisProxima;
    }

    private double calcularDistancia(Entidade e1, Entidade e2) {
        int dx = e1.getX() - e2.getX();
        int dy = e1.getY() - e2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public synchronized void enviarMensagem(Mensagem msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("[CONEXÃO] Erro ao enviar para " + naveId + ": " +
                    e.getMessage());
        }
    }

    public void enviarEstado(EstadoUniverso estado) {
        enviarMensagem(new EventoEstadoUniverso("SERVIDOR", estado));
    }

    private void desconectar() {
        if (naveId != null) {
            galaxia.removerNave(naveId);
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[CONEXÃO] Erro ao fechar: " + e.getMessage());
        }
    }
}