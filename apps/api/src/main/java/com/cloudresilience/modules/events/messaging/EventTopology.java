package com.cloudresilience.modules.events.messaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Version-controlled RabbitMQ topology: durable topic exchange, durable consumer
 * queues, bounded retry queues with a TTL and dead-letter back to the original
 * queue, and durable dead-letter queues. No broker plugin is required.
 */
@Configuration(proxyBeanMethods = false)
public class EventTopology {
    public static final String EVENTS_EXCHANGE = "cloudresilience.events";
    public static final String DEAD_LETTER_EXCHANGE = "cloudresilience.events.dlx";
    public static final String RETRY_EXCHANGE = "cloudresilience.events.retry";
    public static final String NOTIFICATION_QUEUE = "cloudresilience.order.notifications";
    public static final String NOTIFICATION_DLQ = "cloudresilience.order.notifications.dlq";
    public static final String ACTIVITY_QUEUE = "cloudresilience.order.activity";
    public static final String ACTIVITY_DLQ = "cloudresilience.order.activity.dlq";
    public static final String NOTIFICATION_CONSUMER = "order-notifications";
    public static final String ACTIVITY_CONSUMER = "order-activity";
    public static final String NOTIFICATION_KEY = "notifications";
    public static final String ACTIVITY_KEY = "activity";
    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String EVENT_VERSION_HEADER = "eventVersion";
    public static final String CORRELATION_ID_HEADER = "correlationId";
    public static final String RETRY_HEADER = "x-event-retry";
    public static final int MAX_RETRIES = 3;

    private final long[] retryTiersMs;

    public EventTopology(@Value("${app.messaging.retry-tiers-ms:5000,30000,120000}") String tiers) {
        this.retryTiersMs = Arrays.stream(tiers.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                .mapToLong(Long::parseLong).toArray();
        if (retryTiersMs.length == 0) throw new IllegalArgumentException("At least one retry tier is required");
    }

    public long[] retryTiersMs() { return retryTiersMs.clone(); }

    public List<String> retryQueueNames(String consumerKey) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < retryTiersMs.length; index++)
            names.add(queueName(consumerKey, index));
        return List.copyOf(names);
    }

    public String retryQueueName(String consumerKey, int attempt) {
        int index = Math.min(Math.max(attempt - 1, 0), retryTiersMs.length - 1);
        return queueName(consumerKey, index);
    }

    public String retryRoutingKey(String consumerKey, int attempt) {
        int index = Math.min(Math.max(attempt - 1, 0), retryTiersMs.length - 1);
        return consumerKey + "." + index;
    }

    private String queueName(String consumerKey, int index) {
        return "cloudresilience.order." + consumerKey + ".retry." + retryTiersMs[index] + "ms";
    }

    @Bean
    TopicExchange eventsExchange() { return new TopicExchange(EVENTS_EXCHANGE, true, false); }

    @Bean
    TopicExchange deadLetterExchange() { return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean
    DirectExchange retryExchange() { return new DirectExchange(RETRY_EXCHANGE, true, false); }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey("dead." + NOTIFICATION_KEY)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue() { return QueueBuilder.durable(NOTIFICATION_DLQ).build(); }

    @Bean
    Queue activityQueue() {
        return QueueBuilder.durable(ACTIVITY_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey("dead." + ACTIVITY_KEY)
                .build();
    }

    @Bean
    Queue activityDeadLetterQueue() { return QueueBuilder.durable(ACTIVITY_DLQ).build(); }

    @Bean
    Declarables topologyBindings() {
        List<Declarable> declarations = new ArrayList<>();
        declarations.add(BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with("order.#"));
        declarations.add(BindingBuilder.bind(activityQueue()).to(eventsExchange()).with("order.#"));
        declarations.add(BindingBuilder.bind(notificationDeadLetterQueue()).to(deadLetterExchange())
                .with("dead." + NOTIFICATION_KEY));
        declarations.add(BindingBuilder.bind(activityDeadLetterQueue()).to(deadLetterExchange())
                .with("dead." + ACTIVITY_KEY));
        declarations.addAll(retryDeclarations(NOTIFICATION_KEY, NOTIFICATION_QUEUE));
        declarations.addAll(retryDeclarations(ACTIVITY_KEY, ACTIVITY_QUEUE));
        return new Declarables(declarations);
    }

    private List<Declarable> retryDeclarations(String consumerKey, String targetQueue) {
        List<Declarable> declarations = new ArrayList<>();
        for (int index = 0; index < retryTiersMs.length; index++) {
            Queue queue = QueueBuilder.durable(queueName(consumerKey, index))
                    .ttl(Math.toIntExact(retryTiersMs[index]))
                    .deadLetterExchange("")
                    .deadLetterRoutingKey(targetQueue)
                    .maxLength(10_000)
                    .build();
            Binding binding = BindingBuilder.bind(queue).to(retryExchange()).with(consumerKey + "." + index);
            declarations.add(queue);
            declarations.add(binding);
        }
        return declarations;
    }
}
