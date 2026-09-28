package com.example.agent.http;

import com.example.agent.model.ChatRequest;
import com.example.agent.model.ChatResponse;
import com.example.agent.model.ErrorResponse;
import com.example.agent.service.AgentService;
import com.example.agent.storage.StorageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Routes HTTP requests:
 * <ul>
 *   <li>GET  /health          -> 200 {"status":"UP"}</li>
 *   <li>POST /api/agent/chat  -> 200 chat response | 400 invalid input | 503 storage down</li>
 *   <li>anything else         -> 404 (wrong method on a known path -> 405)</li>
 * </ul>
 */
public final class AgentController implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final AgentService agentService;
    private final ObjectMapper mapper;

    public AgentController(AgentService agentService, ObjectMapper mapper) {
        this.agentService = agentService;
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (RuntimeException e) {
            log.error("Unhandled error while processing request", e);
            sendError(exchange, 500, "Internal Server Error", "Unexpected server error");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = normalize(exchange.getRequestURI().getPath());
        String method = exchange.getRequestMethod();
        switch (path) {
            case "/health" -> handleHealth(exchange, method);
            case "/api/agent/chat" -> handleChat(exchange, method);
            default -> sendError(exchange, 404, "Not Found", "No route for " + method + " " + path);
        }
    }

    private void handleHealth(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }
        sendJson(exchange, 200, Map.of("status", "UP"));
    }

    private void handleChat(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            sendError(exchange, 413, "Payload Too Large", "Request body must be at most " + MAX_BODY_BYTES + " bytes");
            return;
        }

        ChatRequest request;
        try {
            request = mapper.readValue(body, ChatRequest.class);
        } catch (JsonProcessingException e) {
            sendError(exchange, 400, "Bad Request", "Request body must be valid JSON, e.g. {\"message\": \"what time is it?\"}");
            return;
        }

        String message = request == null ? null : request.message();
        if (message == null || message.isBlank()) {
            sendError(exchange, 400, "Bad Request", "Field 'message' is required and must not be empty");
            return;
        }

        String sessionId = request.sessionId();
        if (sessionId != null && !sessionId.isBlank() && !SESSION_ID.matcher(sessionId).matches()) {
            sendError(exchange, 400, "Bad Request", "Field 'sessionId' must match [A-Za-z0-9_-]{1,64}");
            return;
        }

        try {
            ChatResponse response = agentService.chat(message.trim(), sessionId);
            sendJson(exchange, 200, response);
        } catch (StorageException e) {
            log.error("Chat history storage failed", e);
            sendError(exchange, 503, "Service Unavailable", "Chat history storage is currently unavailable");
        }
    }

    private static String normalize(String path) {
        return (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendError(exchange, 405, "Method Not Allowed", "Use " + allowed + " for this endpoint");
    }

    private void sendError(HttpExchange exchange, int status, String error, String message) throws IOException {
        sendJson(exchange, status, new ErrorResponse(error, message));
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
