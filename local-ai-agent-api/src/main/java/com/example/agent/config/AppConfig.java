package com.example.agent.config;

import java.time.ZoneId;

/**
 * Application settings, read from environment variables so the same image
 * can run unchanged locally, in docker compose and in CI.
 *
 * @param port         HTTP port (PORT, default 8080)
 * @param awsRegion    AWS region (AWS_REGION, default us-east-1)
 * @param awsEndpoint  custom AWS endpoint, e.g. LocalStack (AWS_ENDPOINT_URL); null = real AWS
 * @param dynamoTable  chat history table (DYNAMODB_TABLE, default ChatHistory)
 * @param sqsQueueName events queue (SQS_QUEUE_NAME, default agent-logs-queue)
 * @param timeZone     zone used for "what time is it?" (AGENT_TIMEZONE, default UTC)
 */
public record AppConfig(
        int port,
        String awsRegion,
        String awsEndpoint,
        String dynamoTable,
        String sqsQueueName,
        ZoneId timeZone) {

    public static AppConfig fromEnv() {
        String endpoint = env("AWS_ENDPOINT_URL", "");
        return new AppConfig(
                Integer.parseInt(env("PORT", "8080")),
                env("AWS_REGION", "us-east-1"),
                endpoint.isEmpty() ? null : endpoint,
                env("DYNAMODB_TABLE", "ChatHistory"),
                env("SQS_QUEUE_NAME", "agent-logs-queue"),
                ZoneId.of(env("AGENT_TIMEZONE", "UTC")));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
