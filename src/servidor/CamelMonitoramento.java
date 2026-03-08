package servidor;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class CamelMonitoramento {
    private CamelContext context;

    public void iniciar() {
        try {
            context = new DefaultCamelContext();

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // tratamento de erros — falha no Camel não afeta o servidor
                    onException(Exception.class)
                            .handled(true)
                            .log("Erro no monitoramento Camel: ${exception.message}");

                    // principal — recebe eventos e processa de forma assíncrona
                    from("direct:eventos-universo")
                            .to("seda:processar-eventos?blockWhenFull=true");

                    // enriquece e grava no log
                    from("seda:processar-eventos?multipleConsumers=true")
                            .process(exchange -> {
                                String evento = exchange.getIn().getBody(String.class);
                                String timestamp = String.valueOf(System.currentTimeMillis());
                                String linhaLog = "[" + timestamp + "] " + evento;
                                exchange.getIn().setBody(linhaLog);
                            })
                            .to("file:logs?fileName=galaxia-eventos.log&fileExist=Append");

                    // eventos críticos — filtra e exibe no console
                    from("seda:processar-eventos?multipleConsumers=true")
                            .filter(body().contains("[CRÍTICO]"))
                            .to("stream:out");
                }
            });

            context.start();
            System.out.println("[CAMEL] Middleware iniciado — log em logs/galaxia-eventos.log");

        } catch (Exception e) {
            System.err.println("[CAMEL] Falha ao iniciar middleware: " + e.getMessage());
        }
    }

    public void publicarEvento(String tipoEvento, String entidadeId,
                               String descricao, int timestampLamport) {
        if (context == null || !context.isStarted()) return;
        try {
            String critico = tipoEvento.equals("DESTRUICAO") ||
                    tipoEvento.equals("VITORIA") ||
                    tipoEvento.equals("DERROTA") ? "[CRÍTICO] " : "";

            String msg = String.format("%sTIPO=%s | ENTIDADE=%s | LAMPORT=L%d | DESC=%s",
                    critico, tipoEvento, entidadeId, timestampLamport, descricao);

            context.createProducerTemplate()
                    .sendBody("direct:eventos-universo", msg);

        } catch (Exception e) {
            System.err.println("[CAMEL] Erro ao publicar evento: " + e.getMessage());
        }
    }

    public void parar() {
        if (context != null) {
            try {
                context.stop();
                System.out.println("[CAMEL] Middleware encerrado");
            } catch (Exception e) {
                System.err.println("[CAMEL] Erro ao encerrar: " + e.getMessage());
            }
        }
    }
}