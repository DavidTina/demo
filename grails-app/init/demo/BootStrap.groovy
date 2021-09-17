package demo

import com.convertlab.redis.RedisService
import consumer.DemoConsumerService

class BootStrap {

    DemoConsumerService demoConsumerService
    RedisService redisService
    def kafkaDeclarer


    def init = { servletContext ->
        redisService.init()
        kafkaDeclarer.run()
        demoConsumerService.start()
    }
    def destroy = {
        demoConsumerService.shutdown()
    }
}
