package com.example.agent.model;

/**
 * Event sent to SQS after every handled chat message.
 * Deliberately contains no message text - only identifiers and metadata.
 */
public record AgentEvent(
        String eventType,
        String messageId,
        String sessionId,
        String intent,
        String timestamp) {
}
