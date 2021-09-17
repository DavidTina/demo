package demo

import com.convertlab.kafka.KafkaProducerService
import com.convertlab.redis.RedisService
import org.springframework.beans.factory.annotation.Value

class DemoController {

    KafkaProducerService kafkaProducerService

    @Value('${kafka.demo.topic}')
    String topic

    def index(){
        String uuid = UUID.randomUUID()
        kafkaProducerService.send(topic, uuid, [demo: "demo", uuid: uuid])
        render "2"
    }
}
