package com.example.agent.events;

import com.example.agent.model.AgentEvent;

/** Port for publishing agent events/logs. Implemented by SQS in production and by fakes in tests. */
public interface EventPublisher {

    /** @throws EventPublishException if the event could not be published */
    void publish(AgentEvent event);
}
