package com.vbforge.asknotes.ollama;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vbforge.asknotes.config.OllamaProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real OllamaClient against a tiny fake Ollama (JDK HttpServer), so we test what really goes over HTTP.
 */
class OllamaClientTest {

    private static final String OK_CHAT =
            "{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"done\":true,\"done_reason\":\"stop\",\"total_duration\":123}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private HttpServer server;
    private volatile HttpHandler handler;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---- request shape ----

    @Test
    void chat_sendsModelOptionsAndFormat_toApiChat() {
        handler = json(200, OK_CHAT);
        OllamaClient client = client(baseUrl(), Duration.ofSeconds(5));

        OllamaChat.Response response = client.chat(
                List.of(OllamaChat.Message.user("hello")),
                Map.of("type", "object"));

        assertThat(response.message().content()).isEqualTo("hi");
        assertThat(lastPath.get()).isEqualTo("/api/chat");

        Map<String, Object> body = lastBodyAsMap();
        assertThat(body).containsEntry("model", "llama3.2:3b").containsEntry("stream", false).containsKey("format");
        assertThat(options(body)).containsEntry("num_ctx", 4096).containsEntry("temperature", 0);
    }

    @Test
    void embedQuery_addsQueryPrefix_andReturnsVector() {
        handler = json(200, "{\"embeddings\":[[0.1,0.2]]}");
        OllamaClient client = client(baseUrl(), Duration.ofSeconds(5));

        float[] vector = client.embedQuery("hello");

        assertThat(vector).containsExactly(0.1f, 0.2f);
        assertThat(lastPath.get()).isEqualTo("/api/embed");
        assertThat(lastBody.get()).contains("search_query: hello");
    }

    @Test
    void embedDocuments_addsDocumentPrefix() {
        handler = json(200, "{\"embeddings\":[[0.1,0.2],[0.3,0.4]]}");
        OllamaClient client = client(baseUrl(), Duration.ofSeconds(5));

        List<float[]> vectors = client.embedDocuments(List.of("one", "two"));

        assertThat(vectors).hasSize(2);
        assertThat(lastBody.get()).contains("search_document: one").contains("search_document: two");
    }

    // ---- bad replies ----

    @Test
    void embed_wrongDimensions_isBadResponse() {
        handler = json(200, "{\"embeddings\":[[0.1,0.2,0.3]]}");   // client expects 2 dimensions

        assertKind(() -> client(baseUrl(), Duration.ofSeconds(5)).embedQuery("x"), OllamaException.Kind.BAD_RESPONSE);
    }

    @Test
    void embed_wrongVectorCount_isBadResponse() {
        handler = json(200, "{\"embeddings\":[[0.1,0.2]]}");

        assertKind(() -> client(baseUrl(), Duration.ofSeconds(5)).embedDocuments(List.of("a", "b")),
                OllamaException.Kind.BAD_RESPONSE);
    }

    @Test
    void chat_cutOffReply_isBadResponse() {
        handler = json(200,
                "{\"message\":{\"role\":\"assistant\",\"content\":\"par\"},\"done\":true,\"done_reason\":\"length\"}");

        assertKind(this::chatOnce, OllamaException.Kind.BAD_RESPONSE);
    }

    @Test
    void chat_replyWithoutMessage_isBadResponse() {
        handler = json(200, "{\"done\":true}");

        assertKind(this::chatOnce, OllamaException.Kind.BAD_RESPONSE);
    }

    // ---- failures ----

    @Test
    void http500_isBadResponse() {
        handler = json(500, "{\"error\":\"boom\"}");

        assertKind(this::chatOnce, OllamaException.Kind.BAD_RESPONSE);
    }

    @Test
    void http404_isBadResponse_withModelHint() {
        handler = json(404, "{\"error\":\"model not found\"}");

        assertThatThrownBy(this::chatOnce)
                .isInstanceOf(OllamaException.class)
                .hasMessageContaining("pulled");
    }

    @Test
    void slowServer_isTimeout() {
        handler = exchange -> {
            try {
                Thread.sleep(1500);
                json(200, OK_CHAT).handle(exchange);
            } catch (Exception ignored) {
                // the client has already given up; nothing to do
            }
        };

        assertKind(() -> client(baseUrl(), Duration.ofMillis(300)).chat(
                List.of(OllamaChat.Message.user("hi")), null), OllamaException.Kind.TIMEOUT);
    }

    @Test
    void nothingListening_isUnavailable() throws IOException {
        String deadUrl = "http://127.0.0.1:" + freePort();

        assertKind(() -> client(deadUrl, Duration.ofSeconds(2)).chat(
                List.of(OllamaChat.Message.user("hi")), null), OllamaException.Kind.UNAVAILABLE);
    }

    // ---- helpers ----

    private void chatOnce() {
        client(baseUrl(), Duration.ofSeconds(5)).chat(List.of(OllamaChat.Message.user("hi")), null);
    }

    private static void assertKind(Runnable call, OllamaException.Kind expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(OllamaException.class)
                .extracting(e -> ((OllamaException) e).kind())
                .isEqualTo(expected);
    }

    private OllamaClient client(String baseUrl, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();

        OllamaProperties props = new OllamaProperties(
                baseUrl, "llama3.2:3b", "nomic-embed-text", 2,
                "search_document: ", "search_query: ",
                Duration.ofSeconds(1), readTimeout, 4096);
        return new OllamaClient(restClient, props);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpHandler json(int status, String body) {
        return exchange -> respond(exchange, status, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastBodyAsMap() {
        return jsonMapper.readValue(lastBody.get(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(Map<String, Object> body) {
        return (Map<String, Object>) body.get("options");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}