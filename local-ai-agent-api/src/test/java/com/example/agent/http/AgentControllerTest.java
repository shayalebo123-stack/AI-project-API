package com.example.agent.http;

import com.example.agent.events.EventPublishException;
import com.example.agent.events.EventPublisher;
import com.example.agent.model.AgentEvent;
import com.example.agent.model.ChatRecord;
import com.example.agent.service.AgentService;
import com.example.agent.storage.ChatHistoryRepository;
import com.example.agent.storage.StorageException;
import com.example.agent.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real HTTP server on a random port with in-memory fakes instead of DynamoDB/SQS,
 * so the tests need neither Docker nor LocalStack.
 */
class AgentControllerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T12:34:56Z"), ZoneId.of("UTC"));

    private final ObjectMapper mapper = Json.newMapper();
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final List<ChatRecord> savedRecords = new CopyOnWriteArrayList<>();
    private final List<AgentEvent> publishedEvents = new CopyOnWriteArrayList<>();
    private AgentServer server;

    @BeforeEach
    void setUp() throws IOException {
        startServer(savedRecords::add, publishedEvents::add);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void startServer(ChatHistoryRepository history, EventPublisher events) throws IOException {
        AgentService service = new AgentService(history, events, FIXED_CLOCK);
        server = new AgentServer(0, new AgentController(service, mapper));
        server.start();
    }

    // ---------- valid input ----------

    @Test
    void health_returns200AndStatusUp() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("UP", mapper.readTree(response.body()).get("status").asText());
    }

    @Test
    void chat_timeQuestionInEnglish_returns200AndCurrentTime() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"What time is it?\"}");

        assertEquals(200, response.statusCode());
        JsonNode json = mapper.readTree(response.body());
        assertEquals("TIME", json.get("intent").asText());
        assertTrue(json.get("reply").asText().contains("12:34:56"), "reply should contain the current time");
        assertFalse(json.get("sessionId").asText().isBlank(), "a session id should be generated");
    }

    @Test
    void chat_timeQuestionInHebrew_returns200AndHebrewReply() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"מה השעה?\"}");

        assertEquals(200, response.statusCode());
        JsonNode json = mapper.readTree(response.body());
        assertEquals("TIME", json.get("intent").asText());
        String reply = json.get("reply").asText();
        assertTrue(reply.contains("השעה"), "reply should be in Hebrew");
        assertTrue(reply.contains("12:34:56"), "reply should contain the current time");
    }

    @Test
    void chat_validRequest_persistsHistoryAndPublishesEvent() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat",
                "{\"message\": \"what time is it\", \"sessionId\": \"demo-session\"}");

        assertEquals(200, response.statusCode());
        assertEquals("demo-session", mapper.readTree(response.body()).get("sessionId").asText());

        assertEquals(1, savedRecords.size());
        ChatRecord record = savedRecords.get(0);
        assertEquals("demo-session", record.sessionId());
        assertEquals("what time is it", record.userMessage());
        assertEquals("TIME", record.intent());
        assertTrue(record.agentReply().contains("12:34:56"));

        assertEquals(1, publishedEvents.size());
        assertEquals("CHAT_INTERACTION", publishedEvents.get(0).eventType());
        assertEquals("demo-session", publishedEvents.get(0).sessionId());
    }

    @Test
    void chat_unknownQuestion_returns200WithFallbackIntent() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"tell me a joke\"}");

        assertEquals(200, response.statusCode());
        assertEquals("UNKNOWN", mapper.readTree(response.body()).get("intent").asText());
    }

    @Test
    void chat_wordContainingTime_isNotTreatedAsTimeQuestion() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"sometimes I wonder\"}");

        assertEquals(200, response.statusCode());
        assertEquals("UNKNOWN", mapper.readTree(response.body()).get("intent").asText());
    }

    // ---------- invalid input ----------

    @Test
    void chat_emptyMessage_returns400() throws Exception {
        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"   \"}");

        assertEquals(400, response.statusCode());
        assertEquals("Bad Request", mapper.readTree(response.body()).get("error").asText());
        assertTrue(savedRecords.isEmpty(), "nothing should be stored for an invalid request");
        assertTrue(publishedEvents.isEmpty(), "nothing should be published for an invalid request");
    }

    @Test
    void chat_missingMessageField_returns400() throws Exception {
        assertEquals(400, post("/api/agent/chat", "{}").statusCode());
    }

    @Test
    void chat_malformedJson_returns400() throws Exception {
        assertEquals(400, post("/api/agent/chat", "this is not json").statusCode());
    }

    @Test
    void chat_emptyBody_returns400() throws Exception {
        assertEquals(400, post("/api/agent/chat", "").statusCode());
    }

    @Test
    void chat_invalidSessionId_returns400() throws Exception {
        assertEquals(400, post("/api/agent/chat", "{\"message\": \"hi\", \"sessionId\": \"bad id!\"}").statusCode());
    }

    // ---------- routing ----------

    @Test
    void unknownPath_returns404() throws Exception {
        HttpResponse<String> response = get("/does-not-exist");

        assertEquals(404, response.statusCode());
        assertEquals("Not Found", mapper.readTree(response.body()).get("error").asText());
    }

    @Test
    void chat_withGet_returns405() throws Exception {
        HttpResponse<String> response = get("/api/agent/chat");

        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElse(""));
    }

    // ---------- failing dependencies ----------

    @Test
    void chat_whenEventPublishingFails_stillReturns200() throws Exception {
        server.stop(0);
        startServer(savedRecords::add, event -> {
            throw new EventPublishException("SQS is down", null);
        });

        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"what time is it?\"}");

        assertEquals(200, response.statusCode());
        assertEquals(1, savedRecords.size(), "history is still stored");
    }

    @Test
    void chat_whenHistoryStorageFails_returns503() throws Exception {
        server.stop(0);
        startServer(record -> {
            throw new StorageException("DynamoDB is down", null);
        }, publishedEvents::add);

        HttpResponse<String> response = post("/api/agent/chat", "{\"message\": \"what time is it?\"}");

        assertEquals(503, response.statusCode());
        assertTrue(publishedEvents.isEmpty(), "no event for an exchange that was not stored");
    }

    // ---------- helpers ----------

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
