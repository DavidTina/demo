package demo

import com.convertlab.redis.RedisService
import consumer.Demo2ConsumerService
import consumer.DemoConsumerService
import org.joda.time.DateTimeZone

class BootStrap {

    DemoConsumerService demoConsumerService
    Demo2ConsumerService demo2ConsumerService
    RedisService redisService
    def kafkaDeclarer


    def init = { servletContext ->
        redisService.init()
        kafkaDeclarer.run()
        demoConsumerService.start()
        demo2ConsumerService.start()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)
    }
    def destroy = {
        demoConsumerService.shutdown()
        demo2ConsumerService.shutdown()
    }
}
