package demo

import com.convertlab.kafka.KafkaProducerService
import grails.converters.JSON
import grails.rest.RestfulController
import org.springframework.beans.factory.annotation.Value
import kafka.ThrottleTopicBuilder

import javax.servlet.http.Cookie

class DemoController extends RestfulController<Demo> {
    static responseFormats = ['json', 'xml']

    DemoController(){
        super(Demo)
    }

    @Value('${kafkaServer.bootstrap.servers}')
    String bootstrapServers

    KafkaProducerService kafkaProducerService
    AwsService awsService
    def chatgptService

    @Value('${kafka.demo.topic}')
    String topic

    @Override
    protected Demo queryForResource(Serializable id){
        Demo.get(id)
    }

    def aiTest(){
        String question = params.question
        def response = chatgptService.sendMessage(question)
        render status: 200, text: response
    }

    def upload(){
//        Part file = request.getPart('file')
        File file = new File("/Users/zhangjun/Desktop/L3相关/2022-7月L3工单.xlsx")
        awsService.upload([ossKey: UUID.randomUUID().toString().replaceAll("-", ""),file:file])
        render "upload"
    }

    def list(){
        awsService.listFile()
        render "list"
    }

    def newObject(){
//        def forTest = new Demo(
//                name: "test23",
//                enableAbtest: false
//        )
//        forTest.save()
        def forTest = ["Math":Math.random()]
        def newCookie = new Cookie("test","3232")
        response.addCookie(newCookie)
        render forTest as JSON
    }
//
    def index(String uuid){
        String throttleTopicName = ThrottleTopicBuilder.newBuilder()
                .setBootstrapServers(bootstrapServers)
                .setMaxPollRecords(5)
                .setRateLimitPerSecond(5 / 100)
                .setTopicName("template-message-batch-send-by-spark-1")
                .setMessageProcessor({ key, msg ->
                    log.warn("ThrottleTopicBuilder get message key is ${key}")
                    kafkaProducerService.send(topic, key, msg)
                }).build()
        kafkaProducerService.send(throttleTopicName,uuid,[demo: "demo", uuid: uuid])
        render "5"
    }
//
    def redirect(){
        forward controller: "demo", action:"index", params: [uuid:UUID.randomUUID().toString().replace("-","")]
    }

    def listDemo(){
        def listDemo = Demo.list()
        log.info("listDemo ${listDemo as JSON}")
        render listDemo as JSON
    }
}
