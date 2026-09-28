package com.example.agent.model;

/** Body returned by POST /api/agent/chat. */
public record ChatResponse(String sessionId, String reply, String intent, String timestamp) {
}
