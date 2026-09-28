package com.example.agent.storage;

import com.example.agent.model.ChatRecord;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

/** Stores chat history in a DynamoDB table (partition key: sessionId, sort key: createdAt). */
public final class DynamoDbChatHistoryRepository implements ChatHistoryRepository {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbChatHistoryRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public void save(ChatRecord record) {
        Map<String, AttributeValue> item = Map.of(
                "sessionId", s(record.sessionId()),
                "createdAt", s(record.createdAt()),
                "messageId", s(record.messageId()),
                "userMessage", s(record.userMessage()),
                "agentReply", s(record.agentReply()),
                "intent", s(record.intent()));
        try {
            dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
        } catch (SdkException e) {
            throw new StorageException("Failed to write chat record to DynamoDB table " + tableName, e);
        }
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
