package com.menome.test

import org.neo4j.driver.v1.AccessMode
import org.neo4j.driver.v1.AuthTokens
import org.neo4j.driver.v1.Driver
import org.neo4j.driver.v1.GraphDatabase
import org.neo4j.driver.v1.Session
import org.neo4j.driver.v1.StatementResult
import org.testcontainers.containers.GenericContainer

class Neo4J {

    static StatementResult run(Driver driver, String statement) {
        Session session = driver.session(AccessMode.WRITE)
        def statementResult = session.run(statement)
        session.close()
        return statementResult
    }

    static def applyTransformationFromFile(Driver driver, String filename) {
        run(driver, new File(filename).text)
    }

    protected static Driver openDriver(GenericContainer neo4JContainer) {
        String boltPort = neo4JContainer.getMappedPort(TestContainerSpecification.NEO4J_BOLT_API_PORT) as String
        String boltURL = "bolt://localhost:$boltPort"
        return GraphDatabase.driver(boltURL, AuthTokens.basic("neo4j", "password"))
    }


}
