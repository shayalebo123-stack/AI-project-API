package com.example.agent.service;

import com.example.agent.events.EventPublisher;
import com.example.agent.model.AgentEvent;
import com.example.agent.model.ChatRecord;
import com.example.agent.model.ChatResponse;
import com.example.agent.storage.ChatHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.UUID;

/**
 * Core agent logic: understand the message, build a reply, persist the exchange in
 * DynamoDB (required) and publish an event to SQS (best effort).
 */
public final class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ChatHistoryRepository history;
    private final EventPublisher events;
    private final Clock clock;

    public AgentService(ChatHistoryRepository history, EventPublisher events, Clock clock) {
        this.history = history;
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param message   non-blank user message
     * @param sessionId existing session id, or null/blank to start a new session
     * @throws com.example.agent.storage.StorageException if the history could not be saved
     */
    public ChatResponse chat(String message, String sessionId) {
        String session = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
        Instant now = clock.instant();
        boolean hebrew = containsHebrew(message);
        Intent intent = Intent.detect(message);
        String reply = buildReply(intent, hebrew, now.atZone(clock.getZone()));
        String messageId = UUID.randomUUID().toString();

        // Required: if history cannot be stored the request fails (StorageException -> HTTP 503).
        history.save(new ChatRecord(session, now.toString(), messageId, message, reply, intent.name()));

        // Best effort: a broken log pipeline must not break the user's conversation.
        try {
            events.publish(new AgentEvent("CHAT_INTERACTION", messageId, session, intent.name(), now.toString()));
        } catch (RuntimeException e) {
            log.warn("Could not publish event for message {}: {}", messageId, e.getMessage());
        }

        log.info("Handled chat message: session={} intent={}", session, intent);
        return new ChatResponse(session, reply, intent.name(), now.toString());
    }

    private static String buildReply(Intent intent, boolean hebrew, ZonedDateTime now) {
        return switch (intent) {
            case TIME -> {
                String time = TIME_FORMAT.format(now);
                String zone = now.getZone().getId();
                yield hebrew
                        ? "השעה עכשיו היא " + time + " (" + zone + ")."
                        : "The current time is " + time + " (" + zone + ").";
            }
            case DATE -> {
                Locale locale = Locale.forLanguageTag(hebrew ? "he" : "en");
                String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(now);
                yield hebrew ? "התאריך היום: " + date + "." : "Today's date is " + date + ".";
            }
            case GREETING -> hebrew
                    ? "שלום! אני הסוכן המקומי. אפשר לשאול אותי מה השעה או מה התאריך."
                    : "Hello! I'm the local agent. You can ask me what time it is or what today's date is.";
            case UNKNOWN -> hebrew
                    ? "לא הבנתי. כרגע אני יודע לענות על שאלות כמו \"מה השעה?\" או \"מה התאריך?\"."
                    : "Sorry, I didn't understand. For now I can answer questions like \"What time is it?\" or \"What's the date?\".";
        };
    }

    private static boolean containsHebrew(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HEBREW);
    }
}
