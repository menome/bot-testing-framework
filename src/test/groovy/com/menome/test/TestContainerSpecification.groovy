package com.menome.test

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.neo4j.driver.v1.AuthTokens
import org.neo4j.driver.v1.Driver
import org.neo4j.driver.v1.GraphDatabase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.output.WaitingConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.utility.MountableFile
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

abstract class TestContainerSpecification extends Specification {

    static Logger log = LoggerFactory.getLogger(ThumbnailBotSpecification.class)

    static final int RABBITMQ_PORT = 5672
    static final int RABBITMQ_MANAGEMENT_PORT = 15672
    static final int NEO4J_BOLT_API_PORT = 7687
    static final int NEO4J_WEB_PORT = 7474
    static final int LIBRARIAN_PORT = 80

    protected GenericContainer createAndStartRabbitMQContainer(Network network) {
        GenericContainer rabbitMQContainer = new GenericContainer("rabbitmq:management-alpine")
                .withNetwork(network)
                .withNetworkAliases("rabbitmq")
                .withExposedPorts(RABBITMQ_PORT)
                .withExposedPorts(RABBITMQ_MANAGEMENT_PORT)
                .withEnv("RABBITMQ_DEFAULT_USER", "menome")
                .withEnv("RABBITMQ_DEFAULT_PASS", "menome")

        rabbitMQContainer.start()

        log.info "Rabbit MQ - http://localhost:${rabbitMQContainer.getMappedPort(RABBITMQ_MANAGEMENT_PORT)}"

        return rabbitMQContainer
    }

    protected Channel openRabbitMQChanel(GenericContainer rabbitMQContainer, String queue, String exchange, String routingKey) {
        ConnectionFactory rabbitConnectionFactory = new ConnectionFactory()
        rabbitConnectionFactory.host = rabbitMQContainer.containerIpAddress
        rabbitConnectionFactory.port = rabbitMQContainer.getMappedPort(RABBITMQ_PORT)
        rabbitConnectionFactory.username = "menome"
        rabbitConnectionFactory.password = "menome"

        Connection rabbitConnection = rabbitConnectionFactory.newConnection()
        Channel rabbitChannel = rabbitConnection.createChannel()
        rabbitChannel.queueDeclare(queue, true, false, false, null)
        rabbitChannel.exchangeDeclare(exchange, "topic", true)
        rabbitChannel.queueBind(queue, exchange, routingKey)

        return rabbitChannel
    }

    protected GenericContainer createAndStartNeo4JContainer(Network network){
        GenericContainer neo4JContainer = new Neo4jContainer("neo4j:3.5.8")
                .withNetwork(network)
                .withNetworkAliases("neo4j")
                .withExposedPorts(NEO4J_WEB_PORT)
                .withExposedPorts(NEO4J_BOLT_API_PORT)

        neo4JContainer.start()

        log.info "Neo4J - Bolt bolt://localhost:${neo4JContainer.getMappedPort(NEO4J_BOLT_API_PORT)}"
        log.info "Neo4J - Web http://localhost:${neo4JContainer.getMappedPort(NEO4J_WEB_PORT)}"

        return neo4JContainer
    }

    protected Driver openNeo4JDriver(GenericContainer neo4JContainer){
        String boltPort = neo4JContainer.getMappedPort(NEO4J_BOLT_API_PORT) as String
        String boltURL = "bolt://localhost:$boltPort"
        return  GraphDatabase.driver(boltURL, AuthTokens.basic("neo4j", "password"));
    }


    GenericContainer createAndStartLibrarianContainer(Network network) {
        GenericContainer librarianContainer = new GenericContainer("menome/file-librarian:latest")
                .withNetworkAliases("librarian")
                .withNetwork(network)
                .withExposedPorts(LIBRARIAN_PORT)
                .waitingFor(new HttpWaitStrategy()
                        .forPath("/")
                        .forPort(LIBRARIAN_PORT)
                        .withStartupTimeout(Duration.ofSeconds(10)))
                .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/config/librarian.config.json"), "/srv/app/config/config.json")

        librarianContainer.start()

        log.info "Librarian -  http://localhost:${librarianContainer.getMappedPort(LIBRARIAN_PORT)}"

        return  librarianContainer

    }

    protected void waitForContainerLogEntry(GenericContainer container, String entry) {
        log.debug("Waiting on container $container.containerInfo.config.image for message '$entry'")
        WaitingConsumer consumer = new WaitingConsumer()
        container.followOutput(consumer, OutputFrame.OutputType.STDOUT)
        consumer.waitUntil({ frame -> frame.getUtf8String().contains(entry) }, 30, log.isDebugEnabled() ? TimeUnit.MINUTES : TimeUnit.SECONDS)
    }

    protected void keepContainersRunningFor10Minutes(){
        log.info("**** Test pausing for 10 minutes ****")
        sleep(600000)
    }
}
