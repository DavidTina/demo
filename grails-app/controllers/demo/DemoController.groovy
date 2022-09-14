package demo

import com.convertlab.kafka.KafkaProducerService
import grails.converters.JSON
import org.springframework.beans.factory.annotation.Value

class DemoController {

    KafkaProducerService kafkaProducerService

    @Value('${kafka.demo.topic}')
    String topic

    def newObject(){
        def forTest = new Demo(
                name: "test23",
                enableAbtest: false
        )
        forTest.save()
        render forTest as JSON
    }

    def index(String uuid){
//        kafkaProducerService.send(topic, uuid, [demo: "demo", uuid: uuid])
        render "5"
    }

    def redirect(){
        forward controller: "demo", action:"index", params: [uuid:"8989"]
    }
}
