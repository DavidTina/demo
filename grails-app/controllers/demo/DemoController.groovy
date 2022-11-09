package demo

import com.convertlab.kafka.KafkaProducerService
import grails.converters.JSON
import grails.rest.RestfulController
import org.springframework.beans.factory.annotation.Value

import javax.servlet.http.Cookie

class DemoController extends RestfulController<Demo> {
    static responseFormats = ['json', 'xml']

    DemoController(){
        super(Demo)
    }

    KafkaProducerService kafkaProducerService
    AwsService awsService

    @Value('${kafka.demo.topic}')
    String topic

    @Override
    protected Demo queryForResource(Serializable id){
        Demo.get(id)
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
//    def index(String uuid){
////        kafkaProducerService.send(topic, uuid, [demo: "demo", uuid: uuid])
//        render "5"
//    }
//
//    def redirect(){
//        forward controller: "demo", action:"index", params: [uuid:"8989"]
//    }
}
