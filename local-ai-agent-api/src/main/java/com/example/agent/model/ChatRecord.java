package com.example.agent.model;

/**
 * One user message plus the agent's answer, as stored in DynamoDB.
 * Table key: partition = sessionId, sort = createdAt (ISO-8601 instant).
 */
public record ChatRecord(
        String sessionId,
        String createdAt,
        String messageId,
        String userMessage,
        String agentReply,
        String intent) {
}
