package com.example.agent.storage;

import com.example.agent.model.ChatRecord;

/** Persistence port for chat history. Implemented by DynamoDB in production and by fakes in tests. */
public interface ChatHistoryRepository {

    /** @throws StorageException if the record could not be stored */
    void save(ChatRecord record);
}
