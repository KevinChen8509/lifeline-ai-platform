package com.project.subscription.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

@Configuration
@EnableKafka
public class KafkaConfig {

    // Spring Boot auto-configures ProducerFactory / ConsumerFactory / KafkaTemplate
    // via application.yml spring.kafka.* properties.
    // This class provides additional customization if needed.
}
