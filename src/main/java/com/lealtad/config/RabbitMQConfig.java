package com.lealtad.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_CLIENTES = "cliente-events-exchange";
    public static final String QUEUE_PUNTOS = "puntos-lealtad-queue";
    public static final String ROUTING_KEY_CLIENTE_CREADO = "cliente.creado";

    @Bean
    public TopicExchange clienteExchange() {
        return new TopicExchange(EXCHANGE_CLIENTES, true, false);
    }

    @Bean
    public Queue puntosLealtadQueue() {
        return new Queue(QUEUE_PUNTOS, true, false, false);
    }

    @Bean
    public Binding binding(Queue puntosLealtadQueue, TopicExchange clienteExchange) {
        return BindingBuilder
                .bind(puntosLealtadQueue)
                .to(clienteExchange)
                .with(ROUTING_KEY_CLIENTE_CREADO);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
