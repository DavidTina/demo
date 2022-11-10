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

    public DemoConsumerService(){
        super(true,1000,"100")
    }

    void processKafkaMessage(String key, Map message) {
        message.each {
            log.warn("otherGroupId DemoConsumerService key is ${it.key} message is ${it.value}")
        }
    }
}
