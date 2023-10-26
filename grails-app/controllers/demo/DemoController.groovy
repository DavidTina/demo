package demo

import com.convertlab.kafka.KafkaProducerService
import grails.converters.JSON
import grails.rest.RestfulController
import kafka.ThrottleTopicBuilder
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.beans.factory.annotation.Value

import javax.servlet.http.Cookie

class DemoController extends RestfulController<Demo> {
    static responseFormats = ['json', 'xml']

    DemoController() {
        super(Demo)
    }

    @Value('${kafkaServer.bootstrap.servers}')
    String bootstrapServers

    KafkaProducerService kafkaProducerService
    AwsService awsService
    def chatgptService
    DmAccessApiService dmAccessApiService

    @Value('${kafka.demo.topic}')
    String topic

    @Override
    protected Demo queryForResource(Serializable id) {
        Demo.get(id)
    }

    def aiTest() {
        String question = params.question
        def response = chatgptService.sendMessage(question)
        render status: 200, text: response
    }

    def upload() {
//        Part file = request.getPart('file')
        File file = new File("/Users/zhangjun/Desktop/L3相关/2022-7月L3工单.xlsx")
        awsService.upload([ossKey: UUID.randomUUID().toString().replaceAll("-", ""), file: file])
        render "upload"
    }

    def list() {
        awsService.listFile()
        render "list"
    }

    def newObject() {
//        def forTest = new Demo(
//                name: "test23",
//                enableAbtest: false
//        )
//        forTest.save()
        def forTest = ["Math": Math.random()]
        def newCookie = new Cookie("test", "3232")
        response.addCookie(newCookie)
        render forTest as JSON
    }
//
    def index(String uuid) {
        String throttleTopicName = ThrottleTopicBuilder.newBuilder()
                .setBootstrapServers(bootstrapServers)
                .setMaxPollRecords(5)
                .setRateLimitPerSecond(5 / 100)
                .setTopicName("template-message-batch-send-by-spark-1")
                .setMessageProcessor({ key, msg ->
                    log.warn("ThrottleTopicBuilder get message key is ${key}")
                    kafkaProducerService.send(topic, key, msg)
                }).build()
        kafkaProducerService.send(throttleTopicName, uuid, [demo: "demo", uuid: uuid])
        render "5"
    }
//
    def redirect() {
        forward controller: "demo", action: "index", params: [uuid: UUID.randomUUID().toString().replace("-", "")]
    }

    def readFileAndUpload() {
        File file = new File("/Users/zhangjun/Desktop/工作簿2.xlsx")
        Workbook wb
        def header = []
        def data = []
        Set identityList = []
        Set tagList = []
        try {
            wb = WorkbookFactory.create(file)
            def sheet = wb.getSheetAt(0)
            sheet.forEach { row ->
                if (row.getRowNum() == 0) {
                    row.forEach { cell ->
                        header << cell.getStringCellValue()
                    }
                } else {
                    def _arr = [
                            issue_key     : row.getCell(0)?.getStringCellValue(),
                            service_aid_id: row.getCell(2)?.getStringCellValue(),
                            summary       : row.getCell(3)?.getStringCellValue(),
                            assignee      : row.getCell(4)?.getStringCellValue(),
                            created       : row.getCell(6)?.getLocalDateTimeCellValue().minusHours(8),
                            develived     : row.getCell(7)?.getNumericCellValue(),
                            priority      : row.getCell(9)?.getStringCellValue(),
                            resolve       : row.getCell(10)?.getStringCellValue(),
                            customer      : row.getCell(11)?.getStringCellValue(),
                            quality       : row.getCell(15)?.getStringCellValue(),
                    ]
                    def component1 = row.getCell(12)?.getStringCellValue()
                    def component2 = row.getCell(13)?.getStringCellValue()
                    if (_arr.assignee) {
                        identityList << _arr.assignee
                    }
                    if (component1) {
                        def draftComponent1 = dmAccessApiService.filterTags(component1)
                        tagList << draftComponent1
                        _arr << ["component1": draftComponent1]
                    }
                    if (component2) {
                        def draftComponent2 = dmAccessApiService.filterTags(component2)
                        tagList << draftComponent2
                        _arr << ["component1": draftComponent2]
                    }
                    data << _arr
                }
            }

        } catch (Exception e) {
            log.error(e)
        } finally {
            wb.close()
            dmAccessApiService.uploadEventToDMhub(data, identityList, tagList)
        }

        def res = ["message": "sucess"]//dmAccessApiService.getEventMetaInfo()
        render res as JSON
    }
}
