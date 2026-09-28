package com.example.agent.events;

import com.example.agent.model.AgentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Publishes events as JSON messages to an SQS queue. The queue URL is resolved lazily and cached. */
public final class SqsEventPublisher implements EventPublisher {

    private final SqsClient sqs;
    private final String queueName;
    private final ObjectMapper mapper;
    private volatile String queueUrl;

    public SqsEventPublisher(SqsClient sqs, String queueName, ObjectMapper mapper) {
        this.sqs = sqs;
        this.queueName = queueName;
        this.mapper = mapper;
    }

    @Override
    public void publish(AgentEvent event) {
        try {
            String body = mapper.writeValueAsString(event);
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(resolveQueueUrl())
                    .messageBody(body)
                    .build());
        } catch (JsonProcessingException | SdkException e) {
            throw new EventPublishException("Failed to publish event to SQS queue " + queueName, e);
        }
    }

    private String resolveQueueUrl() {
        String url = queueUrl;
        if (url == null) {
            url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
            queueUrl = url;
        }
        return url;
    }
}
