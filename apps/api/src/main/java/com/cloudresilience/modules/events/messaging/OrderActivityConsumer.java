package com.cloudresilience.modules.events.messaging;

import com.cloudresilience.modules.events.application.OrderActivityProjection;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Maintains a reporting projection independent of the authoritative order tables. */
@Component
public class OrderActivityConsumer {
    private final EventMessageHandler handler;
    private final OrderActivityProjection projection;

    public OrderActivityConsumer(EventMessageHandler handler, OrderActivityProjection projection) {
        this.handler = handler;
        this.projection = projection;
    }

    @RabbitListener(queues = EventTopology.ACTIVITY_QUEUE, ackMode = "MANUAL",
            autoStartup = "${app.messaging.enabled:true}")
    public void onMessage(Message message, Channel channel) throws IOException {
        handler.handle(message, channel, EventTopology.ACTIVITY_CONSUMER, EventTopology.ACTIVITY_KEY,
                projection::project);
    }
}
