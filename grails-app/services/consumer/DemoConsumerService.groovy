package consumer

import com.convertlab.kafka.KafkaConsumerManager
import org.springframework.beans.factory.annotation.Value

class DemoConsumerService extends KafkaConsumerManager {

    @Value('${kafkaServer.bootstrap.servers}')
    String bootstrapServers

    @Value('${kafka.demo.topic}')
    String topic

    @Value('${kafka.demo.groupId}')
    String groupId

    @Value('${kafka.demo.numConsumers}')
    Integer numConsumers

    @Override
    void processKafkaMessage(String key, Map message) {
        System.out.println("in analysisDef rebuild trigger, message: $message, key is ${key}")
    }
}
