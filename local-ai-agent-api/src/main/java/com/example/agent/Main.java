package com.example.agent;

import com.example.agent.aws.AwsClientFactory;
import com.example.agent.config.AppConfig;
import com.example.agent.events.EventPublisher;
import com.example.agent.events.SqsEventPublisher;
import com.example.agent.http.AgentController;
import com.example.agent.http.AgentServer;
import com.example.agent.service.AgentService;
import com.example.agent.storage.ChatHistoryRepository;
import com.example.agent.storage.DynamoDbChatHistoryRepository;
import com.example.agent.util.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Clock;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnv();
        ObjectMapper mapper = Json.newMapper();

        DynamoDbClient dynamoDb = AwsClientFactory.dynamoDb(config);
        SqsClient sqs = AwsClientFactory.sqs(config);

        ChatHistoryRepository history = new DynamoDbChatHistoryRepository(dynamoDb, config.dynamoTable());
        EventPublisher events = new SqsEventPublisher(sqs, config.sqsQueueName(), mapper);
        AgentService agent = new AgentService(history, events, Clock.system(config.timeZone()));

        AgentServer server = new AgentServer(config.port(), new AgentController(agent, mapper));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.stop(2);
            dynamoDb.close();
            sqs.close();
        }));

        server.start();
        log.info("Local AI Agent API listening on port {} (endpoint override: {}, timezone: {})",
                server.port(),
                config.awsEndpoint() == null ? "none - real AWS" : config.awsEndpoint(),
                config.timeZone());
    }
}
