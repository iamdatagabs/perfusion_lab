package com.perfusionlab.simulation.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import java.net.InetAddress;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationMessagingConfiguration {
    @Bean
    @ConditionalOnMissingBean(RabbitAdmin.class)
    RabbitAdmin simulationRabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    public static final String DEAD_LETTER_EXCHANGE = "perfusionlab.simulation.dlx";
    public static final String DEAD_LETTER_QUEUE = "perfusionlab.simulation.dead";
    public static final String DEAD_LETTER_ROUTING_KEY = "simulation.dead";

    @Bean
    DirectExchange simulationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue simulationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding simulationDeadLetterBinding(
            @Qualifier("simulationDeadLetterQueue") Queue simulationDeadLetterQueue,
            @Qualifier("simulationDeadLetterExchange") DirectExchange simulationDeadLetterExchange) {
        return BindingBuilder.bind(simulationDeadLetterQueue).to(simulationDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    private Queue durableWorkQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    DirectExchange simulationExchange() {
        return new DirectExchange(SimulationJobService.EXCHANGE, true, false);
    }

    @Bean
    Queue generalSimulationQueue() {
        return durableWorkQueue(ExecutionProfile.GENERAL.queueName());
    }

    @Bean
    Queue memorySimulationQueue() {
        return durableWorkQueue(ExecutionProfile.MEMORY.queueName());
    }

    @Bean
    Queue computeSimulationQueue() {
        return durableWorkQueue(ExecutionProfile.COMPUTE.queueName());
    }

    @Bean
    Binding generalSimulationBinding(@Qualifier("generalSimulationQueue") Queue generalSimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(generalSimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.GENERAL.routingKey());
    }

    @Bean
    Binding memorySimulationBinding(@Qualifier("memorySimulationQueue") Queue memorySimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(memorySimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.MEMORY.routingKey());
    }

    @Bean
    Binding computeSimulationBinding(@Qualifier("computeSimulationQueue") Queue computeSimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(computeSimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.COMPUTE.routingKey());
    }

    @Bean
    String workerQueueName(@Value("${perfusion.worker.profile:GENERAL}") String profile) {
        return ExecutionProfile.valueOf(profile.toUpperCase()).queueName();
    }

    @Bean
    String workerNodeQueueName(
            @Value("${perfusion.worker.node-id:}") String configuredNodeId,
            @Value("${perfusion.worker.profile:GENERAL}") String profile) {
        String host;
        try { host = InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) { host = "unknown"; }
        String resolved = configuredNodeId == null || configuredNodeId.isBlank()
                ? host + "-" + profile.toLowerCase()
                : configuredNodeId.trim();
        if (!resolved.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("WORKER_NODE_ID must contain only letters, digits, '_' or '-' and be at most 80 characters");
        }
        return "perfusionlab.simulation.node." + resolved;
    }

    @Bean
    MessageConverter simulationMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}