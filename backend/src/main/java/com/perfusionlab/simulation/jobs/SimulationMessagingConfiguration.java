package com.perfusionlab.simulation.jobs;

import org.springframework.beans.factory.annotation.Value;
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
    DirectExchange simulationExchange() {
        return new DirectExchange(SimulationJobService.EXCHANGE, true, false);
    }

    @Bean
    Queue generalSimulationQueue() {
        return QueueBuilder.durable(ExecutionProfile.GENERAL.queueName()).build();
    }

    @Bean
    Queue memorySimulationQueue() {
        return QueueBuilder.durable(ExecutionProfile.MEMORY.queueName()).build();
    }

    @Bean
    Queue computeSimulationQueue() {
        return QueueBuilder.durable(ExecutionProfile.COMPUTE.queueName()).build();
    }

    @Bean
    Binding generalSimulationBinding(Queue generalSimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(generalSimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.GENERAL.routingKey());
    }

    @Bean
    Binding memorySimulationBinding(Queue memorySimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(memorySimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.MEMORY.routingKey());
    }

    @Bean
    Binding computeSimulationBinding(Queue computeSimulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(computeSimulationQueue).to(simulationExchange)
                .with(ExecutionProfile.COMPUTE.routingKey());
    }

    @Bean
    String workerQueueName(@Value("${perfusion.worker.profile:GENERAL}") String profile) {
        return ExecutionProfile.valueOf(profile.toUpperCase()).queueName();
    }

    @Bean
    MessageConverter simulationMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}