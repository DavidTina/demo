// Place your Spring DSL code here


import com.convertlab.kafka.KafkaProducerService
import com.convertlab.redis.RedisService
import com.convertlab.sql.NativeSqlService
import com.convertlab.chatgpt.service.impl.DefaultChatgptService

beans = {
    nativeSqlService(NativeSqlService)
    redisService(RedisService)
    kafkaProducerService(KafkaProducerService, "demo")
    kafkaDeclarer(KafkaDeclarer)
    chatgptService(DefaultChatgptService)
}
