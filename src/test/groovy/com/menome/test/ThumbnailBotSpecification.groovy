package com.menome.test

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.MountableFile
import spock.lang.Shared

import javax.imageio.ImageIO

class ThumbnailBotSpecification extends TestContainerSpecification {

    static final String RABBITMQ_TEST_EXCHANGE = "fpp"
    static final String RABBITMQ_TEST_ROUTING_KEY = "fpp.thumbnail"
    static final String RABBITMQ_QUEUE_NAME = "thumbnail_queue"
    static final int WEBDAV_PORT = 80

    static Logger log = LoggerFactory.getLogger(ThumbnailBotSpecification.class)

    @Shared
    Network network = Network.newNetwork()
    @Shared
    GenericContainer rabbitMQContainer
    @Shared
    GenericContainer neo4JContainer
    @Shared
    GenericContainer librarianContainer

    def setup() {
    }

    def setupSpec() {
        rabbitMQContainer = createAndStartRabbitMQContainer(network)
        neo4JContainer = createAndStartNeo4JContainer(network)
        librarianContainer = createAndStartLibrarianContainer(network)
    }

    def cleanupSpec() {
        rabbitMQContainer.stop()
        neo4JContainer.stop()
        librarianContainer.stop()
    }


    def createAndStartWebDavContainer(Network network, def fileToMount) {
        GenericContainer webdavContainer = new GenericContainer("bytemark/webdav")
                .withNetwork(network)
                .withNetworkAliases("webdav")
                .withExposedPorts(WEBDAV_PORT)
                .withEnv("ANONYMOUS_METHODS", "ALL")
                .withCopyFileToContainer(MountableFile.forHostPath(new File("src/test/resources/testdata/${fileToMount}").getPath()), "/var/lib/dav/data/${fileToMount}")
        webdavContainer.start()

        log.info("WebDav - http://localhost:${webdavContainer.getMappedPort(WEBDAV_PORT)} with file:$fileToMount")

        return webdavContainer
    }


    GenericContainer createAndStartThumbnailBotContainer(Network network) {
        def thumbnailBotContainer = new GenericContainer("menome/thumbnailbot:latest")
                .withNetwork(network)
                .withNetworkAliases("thumbnail-bot")
                .withCopyFileToContainer(MountableFile.forHostPath(new File("src/test/resources/config/config.json.template").getPath()), "/srv/app/config/config.json")

        thumbnailBotContainer.start()

        return thumbnailBotContainer
    }

    def "test create thumbnail with valid pdf file"() {
        given:
        def neo4jDriver = Neo4J.openDriver(neo4JContainer)
        def session = neo4jDriver.session()
        def rabbitChannel = openRabbitMQChanel(rabbitMQContainer, RABBITMQ_QUEUE_NAME, RABBITMQ_TEST_EXCHANGE, RABBITMQ_TEST_ROUTING_KEY)
        def fileToProcess = "valid.pdf"
        def webdavContainer = createAndStartWebDavContainer(network, fileToProcess)
        def thumbnailBotContainer = createAndStartThumbnailBotContainer(network)
        def msgUUid = UUID.randomUUID()
        def msg = /{"Library":"webdav","Path":"${fileToProcess}","Uuid":"${msgUUid}","Mime":"application\/pdf"}/

        when:
        rabbitChannel.basicPublish(RABBITMQ_TEST_EXCHANGE, RABBITMQ_TEST_ROUTING_KEY, null, msg.getBytes())
        log.debug "Published message to rabbit."
        log.debug msg

        then:

        waitForContainerLogEntry(thumbnailBotContainer, "No next routing key.")
        // Is the resulting file an image? Will thrown an exception if it's not
        def sUrl = "http://localhost:${webdavContainer.getMappedPort(WEBDAV_PORT)}/${msgUUid}/page-thumb-1.png"
        ImageIO.read(new URL(sUrl))

        // Neo4J tests

        def neoQuery = "MATCH (Card {Uuid: \"${msgUUid}\"}) RETURN Card.Thumbnail,Card.ThumbnailLibrary,Card.Uuid"
        def result = session.run(neoQuery).single().asMap()
        result."Card.Uuid" == msgUUid as String
        result."Card.ThumbnailLibrary" == "webdav"
        result."Card.Thumbnail" == "/$msgUUid/page-thumb-1.png"

        cleanup:
        session.close()
        neo4jDriver.close()
        rabbitChannel.close()
        webdavContainer.stop()
        thumbnailBotContainer.stop()
    }



}
