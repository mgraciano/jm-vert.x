package jm.sockjs;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.platform.Verticle;

public class App extends Verticle {

    private static final String CANAL_MENSAGEM = "jm.mensagem";

    @Override
    public void start() {
        final Logger logger = container.logger();
        final EventBus eventBus = vertx.eventBus();
        final HttpServer servidor = vertx.createHttpServer();

        // Cria manipulador de chamadas HTTP para servir página estáticas
        servidor.requestHandler((HttpServerRequest requisicao) -> {
            if (requisicao.path().equals("/")) {
                requisicao.response().sendFile("sockjs/index.html");
            }
        });

        // Cria a instala uma aplicação SockJS
        final SockJSServer sockServer = vertx.createSockJSServer(servidor);
        sockServer.installApp(new JsonObject().putString("prefix", "/app"),
                (final SockJSSocket socket) -> {
                    socket.dataHandler((Buffer mensagem) -> {
                        // Publica a mensagem recebida no event bus
                        eventBus.publish(CANAL_MENSAGEM, mensagem);
                    });

                    // Registra manipulador do event bus apropriadamente
                    eventBus.registerHandler(CANAL_MENSAGEM, new SockJSMensagemHandler(socket));

                    // Tratamento de eventuais exceções
                    socket.exceptionHandler((Throwable event) -> {
                        logger.error("Erro em conexão com o socket.", event);
                    });
                });

        final String pastaUsuario = System.getProperty("user.home");
        // Registra uso de arquivo no sistema de arquivos local, na pasta de usuário
        vertx.fileSystem().open(pastaUsuario + "/mensagens-log.dat", (AsyncResult<AsyncFile> resultado) -> {
            if (resultado.succeeded()) {
                final AsyncFile arquivo = resultado.result();
                // Registra manipulador do event bus para registro em arquivo de log
                eventBus.registerHandler(CANAL_MENSAGEM, new ArquivoMensagemHandler(arquivo));
            } else {
                logger.error("Falha ao abrir arquivo.", resultado.cause());
            }
        });

        // Inicializa servidor na porta 8080
        servidor.listen(8080);
    }

    private static class SockJSMensagemHandler implements Handler<Message<Buffer>> {

        private final SockJSSocket socket;

        SockJSMensagemHandler(final SockJSSocket socket) {
            this.socket = socket;
        }

        @Override
        public void handle(Message<Buffer> evento) {
            // Toda mensagem encaminhada no event bus é repassada para todos os sockets publicados
            socket.write(evento.body());
        }

    }

    private static class ArquivoMensagemHandler implements Handler<Message<Buffer>> {

        private final AsyncFile arquivo;

        ArquivoMensagemHandler(final AsyncFile arquivo) {
            this.arquivo = arquivo;
        }

        @Override
        public void handle(Message<Buffer> evento) {
            // Toda mensagem encaminhada no event bus é repassada os arquivos publicados
            arquivo.write(evento.body().appendString("\n"));
            arquivo.flush();
        }

    }
}
