package demo

import com.convertlab.kafka.KafkaProducerService
import com.convertlab.redis.RedisService
import com.convertlab.rest.HttpClient
import com.convertlab.serializer.MessageSerializer
import com.convertlab.util.CommonUtils
import com.google.gson.Gson
import common.JsonUtil
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.Base64Utils

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

@Transactional
class DmAccessApiService {

    @Value('${DmHub.appid}')
    String appid

    @Value('${DmHub.secret}')
    String secret

    @Value('${kafka.demo.topic}')
    String topic

    RedisService redisService
    KafkaProducerService kafkaProducerService

    final static String baseUrl = "https://api.convertlab.com/"

    final static String cookie = "_icla=1821912358920598310.1852299945; acw_tc=707c9fc316982260441621203e151a075b51f6f0bc40311b8680b31a57da67; SESSION=bcca6c91-04d6-4366-911a-e1b7ee316f1b"

    private static MessageSerializer<Map> serializer;

    static {
        try {
            serializer = (MessageSerializer<Map>) Class.forName(MessageSerializer.serializerClass).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            println("instancing KafkaProducerService failed.");
        }
    }

    def getToken() {
        def token = ""
        try {
            def tokenKey = "${appid}::access_token"
            redisService.withRedis { Jedis ->
                token = Jedis.get(tokenKey)
            }
            if (!token) {
                def url = "${baseUrl}/v2/oauth2/token?grant_type=client_credentials"
                HttpHeaders headers = new HttpHeaders()
                def authToken = Base64Utils.encodeToString("${appid}:${secret}".toString().getBytes(StandardCharsets.UTF_8))
                headers.set(HttpHeaders.AUTHORIZATION, String.format("Basic %s", authToken))
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED)
                HttpEntity entity = new HttpEntity(headers)
                def response = HttpClient.restTemplate.exchange(url, HttpMethod.POST, entity, String.class)

                log.info("=== url: ${url}, response: ${response.getBody()}")
                def res = JsonUtil.strToObj(response.getBody(), Map.class)
                if (res.error) {
                    log.warn("can not refresh token error: ${JsonUtil.objToStr(res)}")
                    token = null;
                } else {
                    token = res.access_token
                }
                redisService.withRedis { Jedis ->
                    Jedis.set(tokenKey, token)
                    Jedis.expire(tokenKey, 7200 - 200)
                }
            }
        } catch (Exception e) {
            log.error("====== get token failed", e)
            return null
        } finally {
            return token
        }
    }


    def createCustomerByIdentity(customerData) {
        def token = this.getToken()
        def result = HttpClient.postForObject("${baseUrl}v2/customers?forceUpdate=false&access_token=${token}", customerData)
        log.info("createCustomerByIdentity:${result}")
        return result
    }

    def createCustomerEventByIdentity(eventData) {
        def token = this.getToken()
        def result = HttpClient.postForObject("${baseUrl}v2/customerEvents?access_token=${token}", eventData)
        log.info("createCustomerEventByIdentity:${result}")
        return result
    }

    def createL3Group() {
        def token = this.getToken()
        def result = HttpClient.postForObject("${baseUrl}v2/lists?access_token=${token}", [name: "L3Team"])
        log.info("createL3Group:${result}")
        return result
    }

    def addMemberToGroup(customerList) {
        log.info("customerList is ${customerList}")
        def token = this.getToken()
        def result = HttpClient.postForObject("${baseUrl}v2/listMembers/bulkAdd?access_token=${token}", [[listId: "1627652953215191040", "customerIds": customerList]])
        log.info("addMemberToGroup:${result}")
        return result
    }

    def findCustomerByIdentity(identityType, identityValue) {
        def token = this.getToken()
        def result = HttpClient.getForObject("${baseUrl}v2/customerService/findCustomerByIdentity?access_token=${token}&identityType=${identityType}&identityValue=${identityValue}")
        log.info("findCustomerByIdentity:${result}")
        return result
    }


    def uploadEventToDMhub(data, createCustomerData, tagList) {
//        def groupResult = createL3Group()
//        def customnerIdList = []
        createCustomerData.each { value ->
            def findCustomerResult = findCustomerByIdentity("c_l3", value)
            if (!findCustomerResult?.id) {
                def customerData = [
                        "customer": [
                                "name": value,
                        ],
                        "identity": [
                                "type" : "c_l3",
                                "value": value,
                                "name" : "Conbertlab-L3"
                        ]
                ]
                def createCustomerResult = createCustomerByIdentity(customerData)
                customnerIdList << createCustomerResult.id
            }
            customnerIdList << findCustomerResult.id
        }
//        addMemberToGroup(customnerIdList)
//        def creatTagList = []
//        tagList.each { value ->
//            creatTagList << [name:value]
//        }
//        callInterApi("https://app.convertlab.com/impala/query_content_tag?group=-1&page=1&rows=130&q=&sidx=date_created&sord=desc", [:], cookie, HttpMethod.GET)
//        callInterApi("https://app.convertlab.com/contenttags/batchCreate", [group: 0, tags: creatTagList], cookie, HttpMethod.POST)
        def filterData = this.filterWorkData(data)
    }

    def callInterApi(url, request, cookie, callMethod = HttpMethod.GET) {
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8)
        headers.set("referer", "https://app.convertlab.com/ui/application/spa/index.html")
        headers.set(HttpHeaders.COOKIE, cookie)
        String reqStr = "";
        if (request != null && request instanceof Map) {
            Map map = (Map) request;
            CommonUtils.preProcessMap(map);
            try {
                reqStr = serializer.format(map);
            } catch (Exception e) {
                log.warn("error to format to json", e);
            }
        } else if (request == null) {
            reqStr = "";
        } else if (request instanceof List) {
            reqStr = new Gson().toJson(request);
        } else {
            reqStr = request.toString();
        }
        log.info("reqStr ===${reqStr}")
        HttpEntity entity = new HttpEntity(reqStr, headers)
        ResponseEntity<String> res = com.convertlab.rest.HttpClient.restTemplate.exchange(url, callMethod, entity, String.class, [:])
        log.info("createContentTag response is ${res}")
        if (res.getStatusCode() == HttpStatus.SC_OK) {
            return new JsonSlurper().parseText(res.getBody())
        } else {
            log.info("call $url res is $res")
            return [:]
        }
    }

    private def filterWorkData(data) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        data.eachWithIndex { item, index ->
            def customerEventData = [
                    "identityType"    : "c_l3",
                    "identityValue"   : item.assignee,
                    "event"           : "c_deal_incident",
                    "date"            : formatter.format(item.created),
                    "c_all_time"      : item.develived,
                    "c_reason"        : item.resolve,
                    "c_desc"          : item.summary,
                    "c_service_aid_id": item.service_aid_id,
                    "c_worker"        : item.assignee,
                    "c_level"         : item.priority,
                    "c_customer"      : item.customer,
                    "c_jira"          : item.issue_key,
                    "c_quality"       : item.quality,
                    "tagGroup"        : [:]
            ]
            if (item?.component1) {
                customerEventData.tagGroup.put(item?.component1, 0)
            }
            if (item?.component2) {
                customerEventData.tagGroup.put(item?.component2, 0)
            }
//            kafkaProducerService.send(topic, customerEventData.identityValue, customerEventData)
        }
    }

    def filterTags(component){
        def draftComponent
        if (component == "API2.0") {
            draftComponent = "API2"
        } else if (component == "ETL  Tool") {
            draftComponent = "EtlTool"
        } else if (component == "OPEN API") {
            draftComponent = "OpenApi"
        } else if (component == "impala-query") {
            draftComponent = "ImpalaQuery"
        } else {
            draftComponent = component.replace(" ", "").replace("-", "").replace("_", "").replace(":", "")
        }
        return draftComponent
    }
}
