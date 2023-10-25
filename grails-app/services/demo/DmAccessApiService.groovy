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

    final static String baseUrl = "https://apiv1.convertwork.cn/"

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
        def result = HttpClient.postForObject("${baseUrl}v2/lists?access_token=${token}", [name:"L3Team"])
        log.info("createL3Group:${result}")
        return result
    }

    def addMemberToGroup(customerList) {
        log.info("customerList is ${customerList}")
        def token = this.getToken()
        def result = HttpClient.postForObject("${baseUrl}v2/listMembers/bulkAdd?access_token=${token}", [[listId:"1626788140788330496","customerIds":customerList]])
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
//        createCustomerData.each { value ->
//            def findCustomerResult = findCustomerByIdentity("c_l3", value)
//            if (!findCustomerResult?.id) {
//                def customerData = [
//                        "customer": [
//                                "name": value,
//                        ],
//                        "identity": [
//                                "type" : "c_l3",
//                                "value": value,
//                                "name" : "Conbertlab-L3"
//                        ]
//                ]
//                def createCustomerResult = createCustomerByIdentity(customerData)
//                customnerIdList << createCustomerResult.id
//            }
//            customnerIdList << findCustomerResult.id
//        }
//        addMemberToGroup(customnerIdList)
//        def creatTagList = []
//        tagList.each { value ->
//            creatTagList << [name:value]
//        }
//        listContentTag([:])
//        createContentTag([group: 0, tags: creatTagList])
//        def filterData = this.filterWorkData(data)
    }

    def createContentTag(request) {
        def url = "https://appv1.convertwork.cn/contenttags/batchCreate"
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8)
        headers.set("referer", "https://appv1.convertwork.cn/ui/application/spa/index.html")
        headers.set(HttpHeaders.COOKIE, "_icla=2181234579659281042.1528739983; c__utmc=2181234579659281042.1528739983; device=web-9f213fb3-7886-4716-b6e1-fa1c23473e3f-1697435846420; remember-me=THMxZGY5U1JENERaSzkzeVUyTGtQUSUzRCUzRDoyRFklMkJEcXpUQnBPOXBSWnJkaVFvTmclM0QlM0Q; SESSION=26456dab-3591-4aee-ba76-c257a90fb478; c__utma=1821912358920598310.1852299945.322280731.1698136645.1698205415.14; c__utmb=2181234579659281042.1528739983.1698205415.1698205424.3")
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
        log.info("reqStr${reqStr}")
        HttpEntity entity = new HttpEntity(reqStr, headers)
        def response = com.convertlab.rest.HttpClient.restTemplate.exchange(url, HttpMethod.POST, entity, String.class, [:])
        log.info("createContentTag response is ${response}")
    }

    def listContentTag(request) {
        def url = "https://appv1.convertwork.cn/impala/query_content_tag?group=-1&page=1&rows=130&q=&sidx=date_created&sord=desc"
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8)
        headers.set("referer", "https://appv1.convertwork.cn/ui/application/spa/index.html")
        headers.set(HttpHeaders.COOKIE, "_icla=2181234579659281042.1528739983; c__utmc=2181234579659281042.1528739983; device=web-9f213fb3-7886-4716-b6e1-fa1c23473e3f-1697435846420; remember-me=THMxZGY5U1JENERaSzkzeVUyTGtQUSUzRCUzRDoyRFklMkJEcXpUQnBPOXBSWnJkaVFvTmclM0QlM0Q; SESSION=26456dab-3591-4aee-ba76-c257a90fb478; c__utma=1821912358920598310.1852299945.322280731.1698136645.1698205415.14; c__utmb=2181234579659281042.1528739983.1698205415.1698205424.3")
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
        log.info("reqStr${reqStr}")
        HttpEntity entity = new HttpEntity(reqStr, headers)
        ResponseEntity<String> res = com.convertlab.rest.HttpClient.restTemplate.exchange(url, HttpMethod.GET, entity, String.class, [:])
        log.info("createContentTag response is ${res}")
        def taglist = []
        new JsonSlurper().parseText(res.getBody())?.rows?.each{item ->
            taglist << item.id
        }
        log.info("taglist is ${taglist}")
        createContentTag(taglist)
        return res
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
                    "tagGroup"        : [:]
            ]
            if (item?.component1) {
                customerEventData.tagGroup.put(item?.component1, 0)
            }
            if (item?.component2) {
                customerEventData.tagGroup.put(item?.component2, 0)
            }
            kafkaProducerService.send(topic, customerEventData.assignee, customerEventData)
        }
    }
}
