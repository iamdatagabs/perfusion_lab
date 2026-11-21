package com.perfusionlab.simulation.jobs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationMessagingConfiguration {
    public static final String QUEUE = "perfusionlab.simulation.jobs";

    @Bean
    DirectExchange simulationExchange() {
        return new DirectExchange(SimulationJobService.EXCHANGE, true, false);
    }

    @Bean
    Queue simulationQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding simulationBinding(Queue simulationQueue, DirectExchange simulationExchange) {
        return BindingBuilder.bind(simulationQueue)
                .to(simulationExchange).with(SimulationJobService.ROUTING_KEY);
    }
}
