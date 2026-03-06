package comum.mensagens.comandos;

public class ComandoChat extends Comando {
    private final String mensagem;

    public ComandoChat(String remetente, String mensagem) {
        super(remetente);
        this.mensagem = mensagem;
    }

    public String getMensagem() { return mensagem; }
}