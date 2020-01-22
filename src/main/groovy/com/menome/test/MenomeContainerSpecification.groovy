package com.menome.test

import org.neo4j.driver.v1.Driver
import org.neo4j.driver.v1.StatementResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import spock.lang.Shared

abstract class MenomeContainerSpecification extends TestContainerSpecification{
    @Shared
    protected GenericContainer neo4JContainer
    @Shared
    protected  Network network = Network.newNetwork()
    @Shared
    protected Driver neo4JDriver

    @Shared
    protected GenericContainer rabbitMQContainer


    static Logger log = LoggerFactory.getLogger(MenomeContainerSpecification.class)

    def setupSpec() {
        rabbitMQContainer = createAndStartRabbitMQContainer(network)
        neo4JContainer = createAndStartNeo4JContainer(network)
        neo4JDriver = Neo4J.openDriver(neo4JContainer)
    }

    def cleanupSpec() {
        log.debug("Closing Neo4J Driver")
        neo4JDriver.close()

    }

    StatementResult run(String statement){
        Neo4J.run(neo4JDriver,statement)
    }

    def applyTransformationFromFile(String filename) {
        log.debug("Applying $filename")
        Neo4J.applyTransformationFromFile(neo4JDriver,filename)
    }

    def assertOneResultWithATrueValue(String matchExpression) {
        StatementResult result = run(matchExpression)
        def resultMap = result.single().asMap()
        resultMap.size() == 1
        resultMap.values()[0] == true
    }

    protected Network getDockerNetwork(){
        return network
    }

}
