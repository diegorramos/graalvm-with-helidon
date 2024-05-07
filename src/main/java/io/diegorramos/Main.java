package io.diegorramos;


import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.messaging.Channel;
import io.helidon.messaging.Messaging;
import io.helidon.messaging.connectors.kafka.KafkaConfigBuilder;
import io.helidon.messaging.connectors.kafka.KafkaConnector;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * The application main class.
 */
public class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }


    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        startServer();
    }


    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     */
    static Single<WebServer> startServer() {

        // load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        WebServer server = WebServer.builder(createRouting(config)).config(config.get("server")).addMediaSupport(JsonpSupport.create()).build();

        Single<WebServer> webserver = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webserver.forSingle(ws -> {
            System.out.println("WEB server is up! http://localhost:" + ws.port() + "/greet");
            ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
        }).exceptionallyAccept(t -> {
            System.err.println("Startup failed: " + t.getMessage());
            t.printStackTrace(System.err);
        });

        return webserver;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @param config configuration of this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        SimpleGreetService simpleGreetService = new SimpleGreetService(config);
        GreetService greetService = new GreetService(config);

        HealthSupport health = HealthSupport.builder().addLiveness(HealthChecks.healthChecks()) // Adds a convenient set of checks
                .build();

        Routing.Builder builder = Routing.builder().register(MetricsSupport.create()) // Metrics at "/metrics"
                .register(health) // Health at "/health"
                .register("/simple-greet", simpleGreetService).register("/greet", greetService);


        return builder.build();
    }

    public void kafka() {
        Channel<String> fromKafka = Channel.<String>builder()
                .publisherConfig(KafkaConnector.configBuilder()
                        .bootstrapServers("localhost:9092")
                        .groupId("example-group-1")
                        .topic("messaging-test-topic-1")
                        .autoOffsetReset(KafkaConfigBuilder.AutoOffsetReset.EARLIEST)
                        .enableAutoCommit(false)
                        .keyDeserializer(StringDeserializer.class)
                        .valueDeserializer(StringDeserializer.class)
                        .build()
                )
                .build();

        // Prepare Kafka connector, can be used by any channel
        KafkaConnector kafkaConnector = KafkaConnector.create();

        Messaging.builder()
                .connector(kafkaConnector)
                .subscriber(fromKafka, ReactiveStreams.<Message<String>>builder()
                        //Apply back-pressure, flatMapCompletionStage request one by one
                        .flatMapCompletionStage(message -> {
                            return CompletableFuture.runAsync(() -> {
                                //Do something lengthy
                                Multi.timer(300, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor())
                                        .first()
                                        .await();
                                //Acknowledge message has been consumed
                                message.ack();
                            });
                        }).ignore())
                .build()
                .start();
    }
}
