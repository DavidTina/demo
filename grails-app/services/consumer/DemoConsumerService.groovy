package consumer

import com.convertlab.kafka.KafkaConsumerManager
import demo.DmAccessApiService
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

    DmAccessApiService dmAccessApiService

    public DemoConsumerService() {
//        super(true,1000,"100")
    }

    void processKafkaMessage(String key, Map message) {
        log.warn("otherGroupId DemoConsumerService key is ${key} message is ${message}")
//        dmAccessApiService.createCustomerEventByIdentity(message)
    }
}
