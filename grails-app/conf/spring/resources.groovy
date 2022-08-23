// Place your Spring DSL code here


import com.convertlab.kafka.KafkaProducerService
import com.convertlab.redis.RedisService
import com.convertlab.sql.NativeSqlService

beans = {
    nativeSqlService(NativeSqlService)
    redisService(RedisService)
    kafkaProducerService(KafkaProducerService, "demo")
    kafkaDeclarer(KafkaDeclarer)
}
