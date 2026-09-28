package com.example.agent.model;

/** Body of POST /api/agent/chat. {@code sessionId} is optional. */
public record ChatRequest(String message, String sessionId) {
}
