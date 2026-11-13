package com.cloudresilience.modules.events.messaging;

import com.cloudresilience.modules.events.application.OrderNotificationProjection;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Persists an in-app notification for every order lifecycle event. */
@Component
public class OrderNotificationConsumer {
    private final EventMessageHandler handler;
    private final OrderNotificationProjection projection;

    public OrderNotificationConsumer(EventMessageHandler handler, OrderNotificationProjection projection) {
        this.handler = handler;
        this.projection = projection;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_QUEUE, ackMode = "MANUAL",
            autoStartup = "${app.messaging.enabled:true}")
    public void onMessage(Message message, Channel channel) throws IOException {
        handler.handle(message, channel, EventTopology.NOTIFICATION_CONSUMER, EventTopology.NOTIFICATION_KEY,
                projection::project);
    }
}
