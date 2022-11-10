package kafka;

import com.convertlab.constants.LogConstants;
import com.convertlab.kafka.KafkaMessageProcessor;
import com.convertlab.kafka.KafkaRecordParser;
import com.convertlab.kafka.MessageRecord;
import com.convertlab.serializer.JacksonSerializer;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThrottleTopicBuilder {
    private String bootstrapServers;

    /**
     * Every not null message  will trigger a heartbeat,consumer will exit and topic will delete
     * after timeout.
     */
    private long heartbeatTimeoutMs = 5000L;

    /**
     * The rate of how many permits become available per second. Must be positive.
     * rateLimitPerSecond cant be so small ,it will make kafka rebanlance,default timeout is 300s
     * make sure maxPollRecords/rateLimitPerSecond < (timeout * 2/3)
     * when maxPollRecords/rateLimitPerSecond > (timeout * 2 / 3)  rateLimitPerSecond will be set as
     * (maxPollRecords * 3) / (timeout/2)
     */
    private double rateLimitPerSecond;

    private Properties consumerProperties;

    /**
     * Should start with 'throttle-${uuid[0..8]}-${systemCurrentSeconds}-' like 'throttle-2ff25e34d-1575539410-throttle-test'
     * Auto format if invalid
     */
    private String topicName;

    private int maxPollRecords = 50;

    private long pollTimeoutMs = 1000;

    private KafkaMessageProcessor messageProcessor;

    private KafkaRecordParser recordParser = new KafkaRecordParser(new JacksonSerializer<>());

    private long consumerCloseTimeoutMs = 60L * 1000L;

    private boolean checkConsumerExist = false;

    @SuppressWarnings("UnstableApiUsage")
    private RateLimiter rateLimiter;

    private ThrottleTopicBuilder() {

    }

    public static ThrottleTopicBuilder newBuilder() {
        return new ThrottleTopicBuilder();
    }

    public ThrottleTopicBuilder setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        return this;
    }

    public ThrottleTopicBuilder setHeartbeatTimeoutMs(long heartbeatTimeoutMs) {
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        return this;
    }

    public ThrottleTopicBuilder setRateLimitPerSecond(double rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
        return this;
    }

    public ThrottleTopicBuilder setConsumerProperties(Properties consumerProperties) {
        this.consumerProperties = consumerProperties;
        return this;
    }

    public ThrottleTopicBuilder setTopicName(String topicName) {
        this.topicName = generateUniqueTopicName(topicName);
        return this;
    }

    public ThrottleTopicBuilder setMaxPollRecords(int maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
        return this;
    }

    public ThrottleTopicBuilder setPollTimeoutMs(long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
        return this;
    }

    public ThrottleTopicBuilder setMessageProcessor(KafkaMessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
        return this;
    }

    public ThrottleTopicBuilder setConsumerCloseTimeoutMs(long consumerCloseTimeoutMs) {
        this.consumerCloseTimeoutMs = consumerCloseTimeoutMs;
        return this;
    }

    public ThrottleTopicBuilder setCheckConsumerExist(boolean checkConsumerExist) {
        this.checkConsumerExist = checkConsumerExist;
        return this;
    }

    public String build() {
        validate();
        initRateLimiter();
        createTopic();
        createConsumer();
        return topicName;
    }

    private void validate() {
        if (bootstrapServers == null) {
            System.out.println("bootstrapServers not set");
        }

        if (topicName == null) {
            System.out.println("topic name not set");
        }

        if (rateLimitPerSecond <= 0) {
            System.out.println("rateLimitPerSecond not set or small then 0");
        }

        if (messageProcessor == null) {
            System.out.println("message processor not set");
        }
    }

    private Object withClient(Function<AdminClient, Object> f) {
        AdminClient client = null;
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            client = AdminClient.create(props);
            return f.apply(client);
        } finally {
            if (client != null) {
                client.close();
            }
        }

    }

    private void createTopic() {
        System.out.println("create throttle topic {}");
        System.out.println(topicName);
        withClient(client -> {
            try {
                ListTopicsOptions options = new ListTopicsOptions();
                options.listInternal(false);
                ListTopicsResult existingTopics = client.listTopics(options);
                Set<String> existingTopicNames = existingTopics.names().get();
                if (!existingTopicNames.contains(topicName)) {
                    NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
                    client.createTopics(Collections.singletonList(newTopic)).all().get();
                } else {
                    System.out.println("throttle topic already exist {}");
                    System.out.println(topicName);
                }
            } catch (Exception e) {
                System.out.println("create throttle topic failed ");
                System.out.println(e);
            }
            return topicName;
        });

    }

    private void deleteTopic() {
        System.out.println("delete throttle topic {}");
        System.out.println(topicName);
        withClient(client -> {
            try {
                client.deleteTopics(Collections.singletonList(topicName)).all().get();
            } catch (Exception e) {
                System.out.println("delete throttle topic failed");
                System.out.println(e.toString());
            }
            return topicName;
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private void initRateLimiter() {
        rateLimiter = RateLimiter.create(generateLimitPerSecond());
    }

    private void createConsumer() {
        if (checkConsumerExist && consumerExist()) {
            System.out.println("consumer already exist create failed! ");
            return;
        }

        String tenantId = MDC.get(LogConstants.X_TENANT_ID);
        String upstream = MDC.get(LogConstants.X_UPSTREAM);
        String requestId = MDC.get(LogConstants.X_REQUEST_ID);
        String userId = MDC.get(LogConstants.X_USER_ID);
        String userName = MDC.get(LogConstants.X_USER_NAME);

        ExecutorService singleConsumerThread = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "thread-" + topicName));
        singleConsumerThread.submit(() -> {
            MDC.put(LogConstants.X_TENANT_ID, tenantId);
            MDC.put(LogConstants.X_UPSTREAM, upstream);
            MDC.put(LogConstants.X_REQUEST_ID, requestId);
            MDC.put(LogConstants.X_USER_ID, userId);
            MDC.put(LogConstants.X_USER_NAME, userName);

            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(resolveProperties());
            consumer.subscribe(Collections.singletonList(topicName));
            long lastCheckMs = System.currentTimeMillis();
            try {
                while (true) {
                    try {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollTimeoutMs));
                        List<MessageRecord> messages = recordParser.parse(records);
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - lastCheckMs  > heartbeatTimeoutMs && messages.isEmpty()) {
                            System.out.println("check failed exit throttle topic ");
                            break;
                        }
                        if (!messages.isEmpty()) {
                            processMessage(messages);
                            lastCheckMs = nowMs;
                        }
                        System.out.println("wait----");
                        System.out.println(LocalDateTime.now());
                    } catch (WakeupException e) {
                        System.out.println("exist kafka throttle consumer");
                        return;
                    } catch (Exception e) {
                        System.out.println("kafka throttle consumer unexpected error occurred");
                        System.out.println(e.toString());
                        return;
                    }

                }
            } finally {
                consumer.close(Duration.ofMillis(consumerCloseTimeoutMs));
                deleteTopic();
                singleConsumerThread.shutdown();
            }

        });
    }

    private void processMessage(List<MessageRecord> messages) {
        try {
            messages.forEach(messageRecord -> {
                rateLimiter.acquire();
                messageProcessor.processKafkaMessage(messageRecord.getKey(), messageRecord.getValue());
            });
        } catch (Exception e) {
            System.out.println("throttle consumer error occurred when process message");
            System.out.println(e.toString());
        }
    }

    private Properties resolveProperties() {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, topicName + "-groupId");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(300 * 1000));
        props.setProperty(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(320 * 1000));
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));
        if (consumerProperties != null) {
            consumerProperties.forEach((key, value) -> props.setProperty(String.valueOf(key), String.valueOf(value)));
        }
        return props;
    }

    private String generateUniqueTopicName(String topicName) {
        Pattern pattern = Pattern.compile("throttle-.{8}-[1-9][0-9]*-.*");
        Matcher matcher = pattern.matcher(topicName);
        if (!matcher.matches()) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            topicName = "throttle-" + uuid.substring(0, 8) + "-" + new Date().getTime() / 1000 + "-" + topicName;
        }
        return topicName;
    }

    private boolean consumerExist() {
        return (boolean) withClient(client -> {
            try {
                List<String> allGroups = client.listConsumerGroups()
                        .valid()
                        .get()
                        .stream()
                        .map(ConsumerGroupListing::groupId)
                        .filter(groupId -> groupId.startsWith(topicName))
                        .collect(Collectors.toList());

                Map<String, ConsumerGroupDescription> allGroupDetails =
                        client.describeConsumerGroups(allGroups).all().get();

                return allGroupDetails.entrySet().stream().anyMatch(entry -> {
                    ConsumerGroupDescription description = entry.getValue();
                    return description.members().stream()
                            .map(des -> des.assignment().topicPartitions().stream().map(TopicPartition::topic).collect(Collectors.toSet()))
                            .anyMatch(tps -> tps.contains(topicName));

                });
            } catch (Exception e) {
                System.out.println("list consumer group failed!");
                System.out.println(e);
            }
            return false;
        });
    }

    private double generateLimitPerSecond() {
        double maxPollIntervalSecond = 300d;
        if (consumerProperties != null) {
            maxPollIntervalSecond = Integer.valueOf(consumerProperties
                    .getProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000")) / 1000d;
        }

        if (maxPollRecords / rateLimitPerSecond > maxPollIntervalSecond * 2d / 3d) {
            return (maxPollRecords * 3d) / (maxPollIntervalSecond * 2d);
        }
        return rateLimitPerSecond;
    }

}
