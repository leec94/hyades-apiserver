package org.dependencytrack.event.kafka;

import org.dependencytrack.event.kafka.KafkaTopics.Topic;

import java.util.Map;

public record KafkaEvent<K, V>(Topic<K, V> topic, K key, V value, Map<String, String> headers) {

    public KafkaEvent(final Topic<K, V> topic, final K key, final V value) {
        this(topic, key, value, null);
    }

}
