package comum;

public enum TipoEntidade {
    NAVE_REBELDE("Rebel", 'O'),
    NAVE_IMPERIAL("TIE Fighter", '▲'),
    BASE_REBELDE("Base Rebelde", 'B'),
    STAR_DESTROYER("Estrela da Morte", 'S'),
    PLANETA("Planeta", '*'),
    VAZIO("Vazio", ' ');

    private final String nome;
    private final char simbolo;

    TipoEntidade(String nome, char simbolo) {
        this.nome = nome;
        this.simbolo = simbolo;
    }

    public String getNome() { return nome; }
    public char getSimbolo() { return simbolo; }
}