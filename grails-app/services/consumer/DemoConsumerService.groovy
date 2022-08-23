package consumer

import com.convertlab.kafka.KafkaConsumerManager
import org.springframework.beans.factory.annotation.Value

class DemoConsumerService extends KafkaConsumerManager {

    @Value('${kafka.demo.numConsumers}')
    Integer numConsumers
    @Value('${kafka.demo.groupId}')
    String groupId
    @Value('${kafka.demo.topic}')
    String topic
    @Value('${kafkaServer.bootstrap.servers}')
    String bootstrapServers

    void processKafkaMessage(String key, Map message) {
        log.info("DemoConsumerService key is ${key} message is ${message}")
    }
}
