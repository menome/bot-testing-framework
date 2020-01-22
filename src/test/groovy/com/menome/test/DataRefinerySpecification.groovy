package com.menome.test

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.MountableFile

class DataRefinerySpecification extends MenomeContainerSpecification {

    static Logger log = LoggerFactory.getLogger(DataRefinerySpecification.class)

    GenericContainer createAndStartDataRefineryContainer(Network network) {
        def dataRefineryBotContainer = new GenericContainer("menome/datarefinery:latest")
                .withNetwork(network)
                .withNetworkAliases("datarefinery-bot")
                .withCopyFileToContainer(MountableFile.forHostPath(new File("src/test/resources/config/config.json.template").getPath()), "/srv/app/config/config.json")

        dataRefineryBotContainer.start()

        return dataRefineryBotContainer
    }

    def "test Konrad Employee node created"() {
        given:
        def dataRefineryContainer = createAndStartDataRefineryContainer(network)
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

        expect:
        assertOneResultWithATrueValue("match(s:Employee{Name:'Konrad Aust'}) return count(s) =1")

        cleanup:
        dataRefineryContainer.stop()
        rabbitChannel.close()
    }
}
