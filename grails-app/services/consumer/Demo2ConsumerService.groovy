package consumer

import com.convertlab.kafka.KafkaConsumerManager
import org.springframework.beans.factory.annotation.Value

class Demo2ConsumerService extends KafkaConsumerManager {

    @Value('${kafka.demo.numConsumers}')
    Integer numConsumers
    @Value('${kafka.demo.otherGroupId}')
    String groupId
    @Value('${kafka.demo.topic}')
    String topic
    @Value('${kafkaServer.bootstrap.servers}')
    String bootstrapServers

    public Demo2ConsumerService(){
        super(true,1000,"100")
    }

    void processKafkaMessage(String key, Map message) {
        log.warn("otherGroupId DemoConsumerService key is ${key} message is ${message}")
    }
}
