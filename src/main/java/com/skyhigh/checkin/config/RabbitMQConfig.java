package com.skyhigh.checkin.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "skyhigh.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "skyhigh.events";
    public static final String SEAT_RELEASED_QUEUE = "seat.released";
    public static final String WAITLIST_OFFER_QUEUE = "waitlist.offer";
    public static final String WAITLIST_NOTIFICATION_QUEUE = "waitlist.notification";

    public static final String SEAT_RELEASED_ROUTING_KEY = "seat.released";
    public static final String WAITLIST_OFFER_ROUTING_KEY = "waitlist.offer";
    public static final String WAITLIST_NOTIFICATION_ROUTING_KEY = "waitlist.notification";

    @Bean
    public TopicExchange skyhighExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue seatReleasedQueue() {
        return QueueBuilder.durable(SEAT_RELEASED_QUEUE).build();
    }

    @Bean
    public Queue waitlistOfferQueue() {
        return QueueBuilder.durable(WAITLIST_OFFER_QUEUE).build();
    }

    @Bean
    public Queue waitlistNotificationQueue() {
        return QueueBuilder.durable(WAITLIST_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding seatReleasedBinding(Queue seatReleasedQueue, TopicExchange skyhighExchange) {
        return BindingBuilder.bind(seatReleasedQueue).to(skyhighExchange).with(SEAT_RELEASED_ROUTING_KEY);
    }

    @Bean
    public Binding waitlistOfferBinding(Queue waitlistOfferQueue, TopicExchange skyhighExchange) {
        return BindingBuilder.bind(waitlistOfferQueue).to(skyhighExchange).with(WAITLIST_OFFER_ROUTING_KEY);
    }

    @Bean
    public Binding waitlistNotificationBinding(Queue waitlistNotificationQueue, TopicExchange skyhighExchange) {
        return BindingBuilder.bind(waitlistNotificationQueue).to(skyhighExchange).with(WAITLIST_NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}

