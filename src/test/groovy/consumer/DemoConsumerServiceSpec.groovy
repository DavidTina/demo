package consumer

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class DemoConsumerServiceSpec extends Specification implements ServiceUnitTest<DemoConsumerService>{

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect:"fix me"
            true == false
    }
}
