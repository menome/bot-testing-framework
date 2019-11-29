package com.menome.test


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.MountableFile
import spock.lang.Shared

class DataRefinerySpecification extends TestContainerSpecification {

    static Logger log = LoggerFactory.getLogger(DataRefinerySpecification.class)

    @Shared
    Network network = Network.newNetwork()
    @Shared
    GenericContainer rabbitMQContainer
    @Shared
    GenericContainer neo4JContainer

    def setupSpec() {
        rabbitMQContainer = createAndStartRabbitMQContainer(network)
        neo4JContainer = createAndStartNeo4JContainer(network)
    }

    GenericContainer createAndStartDataRefineryContainer(Network network) {
        def dataRefineryBotContainer = new GenericContainer("menome/datarefinery:latest")
                .withNetwork(network)
                .withNetworkAliases("datarefinery-bot")
                .withCopyFileToContainer(MountableFile.forHostPath(new File("src/test/resources/config/config.json.template").getPath()), "/srv/app/config/config.json")

        dataRefineryBotContainer.start()

        return dataRefineryBotContainer

    }

    def "test data refinery with example message"() {
        given:
        def dataRefineryContainer = createAndStartDataRefineryContainer(network)
        def neo4jDriver = openNeo4JDriver(neo4JContainer)
        def neo4Jsession = neo4jDriver.session()
        def routingKey = "syncevents.harvester.updates.*"
        def queue = "refineryQueue"
        def exchange = "syncevents"
        def rabbitChannel = openRabbitMQChanel(rabbitMQContainer, queue, exchange, routingKey)
        def msg = '''{"Name":"Konrad Aust","NodeType":"Employee","Priority": 1,"SourceSystem": "HRSystem","ConformedDimensions": {"Email": "konrad.aust@menome.com","EmployeeId": 12345},"Properties": {"Status":"active","PreferredName": "The Chazzinator","ResumeSkills": "programming,peeling bananas from the wrong end,handstands,sweet kickflips"},"Connections": [{"Name": "Menome Victoria","NodeType": "Office","RelType": "LocatedInOffice","ForwardRel": true,"ConformedDimensions": {"City": "Victoria"}},{"Name": "theLink","NodeType": "Project","RelType": "WorkedOnProject","ForwardRel": true,"ConformedDimensions": {"Code": "5"}},{"Name": "theLink Product Team","NodeType": "Team","Label": "Facet","RelType": "HAS_FACET","ForwardRel": true,"ConformedDimensions": {"Code": "1337"}}]}'''
        when:
        log.debug(msg)
        rabbitChannel.basicPublish(exchange, routingKey, null, msg.getBytes())

        then:
        waitForContainerLogEntry(dataRefineryContainer, "Success for Employee message: Konrad Aust")

        cleanup:
        rabbitChannel.close()
        neo4Jsession.close()
        neo4jDriver.close()
        dataRefineryContainer.stop()

    }

}
