package comum;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServicoDescoberta {
    private static final int PORTA_DISCOVERY = 5556;
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private Map<String, InfoServidor> servidoresConhecidos;
    private DatagramSocket socket;
    private boolean rodando;

    public ServicoDescoberta() {
        this.servidoresConhecidos = new ConcurrentHashMap<>();
        this.rodando = true;
    }

    public void anunciarServidor(String tipo, String nome, int porta) {
        new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                MulticastSocket mSocket = new MulticastSocket();

                while (rodando) {
                    String msg = String.format("SERVIDOR:%s:%s:%d", tipo, nome, porta);
                    byte[] buffer = msg.getBytes();
                    DatagramPacket packet = new DatagramPacket(
                            buffer, buffer.length, group, PORTA_DISCOVERY
                    );
                    mSocket.send(packet);
                    Thread.sleep(5000); // Anuncia a cada 5 segundos
                }
                mSocket.close();
            } catch (Exception e) {
                System.err.println("[DISCOVERY] Erro ao anunciar: " + e.getMessage());
            }
        }).start();
    }

    public void escutarServidores() {
        new Thread(() -> {
            try {
                MulticastSocket mSocket = new MulticastSocket(PORTA_DISCOVERY);
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                mSocket.joinGroup(group);

                byte[] buffer = new byte[256];

                while (rodando) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    mSocket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());
                    processarAnuncio(msg, packet.getAddress());
                }

                mSocket.leaveGroup(group);
                mSocket.close();
            } catch (Exception e) {
                if (rodando) {
                    System.err.println("[DISCOVERY] Erro ao escutar: " + e.getMessage());
                }
            }
        }).start();
    }

    private void processarAnuncio(String msg, InetAddress address) {
        String[] partes = msg.split(":");
        if (partes.length == 4 && partes[0].equals("SERVIDOR")) {
            String tipo = partes[1];
            String nome = partes[2];
            int porta = Integer.parseInt(partes[3]);

            String chave = address.getHostAddress() + ":" + porta;
            InfoServidor info = new InfoServidor(tipo, nome, address.getHostAddress(), porta);

            if (!servidoresConhecidos.containsKey(chave)) {
                servidoresConhecidos.put(chave, info);
                System.out.println("[DISCOVERY] Servidor descoberto: " + info);
            }
        }
    }

    public List<InfoServidor> listarServidores() {
        return new ArrayList<>(servidoresConhecidos.values());
    }

    public InfoServidor buscarServidor(String tipo) {
        for (InfoServidor info : servidoresConhecidos.values()) {
            if (info.getTipo().equals(tipo)) {return info;}
        }return null;
    }

    public void parar() {
        rodando = false;
    }

    public static class InfoServidor implements Serializable {
        private final String tipo; // "GALAXIA", "BASE_REBELDE", "BASE_IMPERIAL"
        private final String nome;
        private final String host;
        private final int porta;

        public InfoServidor(String tipo, String nome, String host, int porta) {
            this.tipo = tipo;
            this.nome = nome;
            this.host = host;
            this.porta = porta;
        }

        public String getTipo() { return tipo; }
        public String getNome() { return nome; }
        public String getHost() { return host; }
        public int getPorta() { return porta; }

        @Override
        public String toString() {
            return String.format("[%s] %s em %s:%d", tipo, nome, host, porta);
        }
    }
}