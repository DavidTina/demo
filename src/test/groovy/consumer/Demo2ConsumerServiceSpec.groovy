package consumer

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class Demo2ConsumerServiceSpec extends Specification implements ServiceUnitTest<Demo2ConsumerService>{

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect:"fix me"
            true == false
    }
}
