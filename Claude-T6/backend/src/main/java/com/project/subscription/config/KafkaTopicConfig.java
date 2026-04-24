package com.project.subscription.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic deviceRawTopic() {
        return TopicBuilder.name("device.raw")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic alertEventTopic() {
        return TopicBuilder.name("alert.event")
            .partitions(3)
            .replicas(1)
            .build();
    }
}
