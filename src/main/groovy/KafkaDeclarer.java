

import com.convertlab.kafka.gen.KafkaTopicGenerator;
import com.convertlab.kafka.gen.KafkaTopicGeneratorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;


@Component
public class KafkaDeclarer {

    private final Logger log = LoggerFactory.getLogger(KafkaDeclarer.class);


    @Value("${kafkaServer.bootstrap.servers}")
    String kafkaServers;

    Short defaultReplications = 1;

    @Value("${kafkaServer.topicDeclarationFile:/kafka/topics.json}")
    String kafkaTopicDeclarationFile;


    @Autowired
    Environment env;


    public void run() throws Exception {
        log.info("=== KafkaDeclarer started");

        KafkaTopicGeneratorBuilder generatorBuilder = new KafkaTopicGeneratorBuilder(kafkaServers)
                .setDefaultReplicas(defaultReplications)
                .setDevMode(true);

        KafkaTopicGenerator generator = generatorBuilder.build();
        generator.doRun();
    }

}
