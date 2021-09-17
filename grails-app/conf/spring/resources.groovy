import com.convertlab.kafka.KafkaProducerService
import com.convertlab.kafka.gen.SchemaValidator
import com.convertlab.redis.RedisService
import com.convertlab.sql.NativeSqlService

// Place your Spring DSL code here

beans = {
    nativeSqlService(NativeSqlService)
    redisService(RedisService)
    kafkaProducerService(KafkaProducerService, "demo")
    topicValidator(SchemaValidator)
    kafkaDeclarer(KafkaDeclarer)
}
