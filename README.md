# Menome Bot Testing Framework

This project is used to test menome bots. It can be used locally to enable local testing as well on the CI server. 

The project makes heavy use of the [TestContainers](https://www.testcontainers.org/) for spinning up docker containers in the test environment
Tests are written using the [Spock](http://spockframework.org/spock/docs) BDD testing framework and[ Groovy](http://groovy-lang.org/).

### Running Tests
These tests require a Java Runtime Environment (JRE) version 8 minimum. 

The easiest way to install a Java Environment is to use SKDMan (https://sdkman.io/). Following intstallation instructions for your preferred platform. 

TL;DR  ``` curl -s "https://get.sdkman.io" | bash```


Install Java 

```sdk install java```

Running tests

```/.gradlew clean test```

You should see output something like

```
./gradlew clean test      

BUILD SUCCESSFUL in 1m 11s
5 actionable tasks: 5 executed
```

A HTML report is generated with the details and can be found in  ```build/reports/tests/test```
 

### Concepts
Part of the challenge of writing tests (particularly with loosely coupled systems like Menome) is figuring out what to test. In general, testing should cover low level details of a particular implementation (bot) and potentially how the bot interacts with other bots. The menome bots typically have a specific job that they do and communicate with other bots by passing messages. Bots may augment the knowledge graph by creating nodes, relationships or updating properties on existing nodes. The graph is refined as new bits of knowledge is added/removed from it. 

It's expected that various types of tests (unit, integration, end-to-end scenario, etc.) will be used to validate the 'system'. The bot-testing-framework can be used to spin up just enough of the infrastructure to test specific scenarios. 

It's important to ask oneself what exactly are we testing for here? Tests should be explicit about what they are testing. They should be named in such a way to convey their meaning. It can be easy to create a test that attempts to validate a bunch of different things. On the surface that might sound good, but those types of tests tend to be brittle and ultimately don't tell us anything useful when they break. 

Tests written in Spock benefit from following the given->when->then pattern. Given an environment that looks like this -> When provided these inputs-> Expect the altered state to look like this

```
def "two plus two should equal four"() {
    given:
        int left = 2
        int right = 2
 
    when:
        int result = left + right
 
    then:
        result == 4
}
```

### Spock specific concepts
New specifications should extend from the TestContainerSpecification or the MenomeContainerSpecification class.
 
TestContainerSpecification class provides a few convenience methods that we'll find useful when writing and debugging tests. 
MenomeContainerSpecification starts Neo4J and RabbitMQ containers (which tend to be used very frequently in tests). It also provides a couple of helper methods for interacting with Neo4J. 

```class ParserBotSpecification extends MenomeContainerSpecification```

Test methods in Spock should be named as clearly as possible. If you're having a hard time finding a name for what it is you're trying to test, that may be an indication that the test is too ambitious and needs to be scaled back. Test names can be quoted and contain spaces. This helps with readability.

 ```def "two plus two should equal four"() {```
 
 #### Test lifecycle
 There are a set of lifecycle methods that help promote reuse when writing Spock tests.
 
 | Method| Description|
 |------|--------|
 | setup() | Method runs before each test|
 | cleanup() | Method runs after each test|
 | setupSpec() | Method runs once before the first test starts|
 | cleanupSpec() | Method runs once after all tests have finished|
 
 One potential use of the setup() method would be to reset the environment to a 'clean' or 'known' state. For example, we might delete all content from a Neo4J container before the test runs. Or perhaps we might bootstrap the graph with a known starting state.
  
 For some tests, it might be a good idea to spin up some of the bot containers once via the setupSpec() method, then close or clean up any additional resources with cleanupSpec()
 
 
 ### Logging
The bot testing framework uses [SLF4J](http://www.slf4j.org/) for logging. Log levels and log message formats are configured in the resources/simplelogger.properties file. Log levels are one of "trace", "debug", "info", "warn", or "error". By default, log messages are logged at the info  com.menome.* package structure. If you want to see lower level messages set that value to debug.
 
Logging messages from specifications can be accomplished by first constructing a logger
 
Typically: 
```static Logger log = LoggerFactory.getLogger(ParserBotSpecification.class)```

log messages are emitted using

```log.info("my fantastic log message)```

or 

```log.debug(my low level debug message)``` 

Log messages are written to standard out (also configured in simplelogger.properties)


### Examples
One of the specifications in this project tests the DataRefineryBot. It spins up the minimum amount of infrastructure (Rabbit, Neo4J and DataRefineryBot). The Rabbit and Neo4J containers are created once for the entire specification via the setupSpec in the MenomeContainerSpecification superclass. Those containers are available to the specification via the protected variables neo4JContainer and rabbitMQContainer.  A docker network is also created in the superclass. Any containers that the specifications create should join that network if the container needs to be visible to other containers in the Docker network.



```groovy
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
```

I've found it convenient to create individual methods to create the necessary containers. It seems to be typical that a specification will require a similar set of containers and having a createAndStart method for each of them makes sense to me.

### Waiting strategies
As mentioned above, testing asynchronous code can be tricky. We need to get the overall system into a state where we can make assertions around expected results. In tests, the number of moving parts is fairly small so it should be reasonable to expect that there are points in time where the containers will be 'done' processing messages. There are two primary strategies that I've used that work consistently. 

The first one (illustrated above) is waitForContainerLogEntry. This is a helper method that blocks execution until a message appears in standard out of the container or it times out. There are two implementations of this method the one illustrated here will wait for 30 seconds (30 minutes if the com.menome log level is set to debug). There is also another implementation that takes a Duration so a test can provide it's own timeout value. 

The second approach is to use the await implementation from [Awaitility](https://github.com/awaitility/awaitility). It's a more generic approach that blocks until some condition is true.   

```await().atMost(5, TimeUnit.MINUTES).until { someConditionalMethod == true }```

This requires the following gradle dependency for any projects that require it

```compile 'org.awaitility:awaitility-groovy:4.0.2'```

### Debugging Hints
Debugging tests with the test containers implementation can be a bit tricky as the containers get destroyed after the test completes/fails. I've found it helpful to keep containers up and running while sorting out configuration issues or looking for specific log messages, system state etc. 

```keepContainersRunningFor10Minutes()```

keepContainersRunningFor10Minutes() is a simple way to keep docker containers up and running. It is simply a wrapper around a sleep(600000).

If specifications use the MenomeContainerSpecification as a base class, URLs for Neo4J and Rabbit will be displayed to standard out. This is helpful as the test containers package will run those containers on random ports (to avoid tests colliding with each other)


### Building this library
This project is intended to be used by other project specific tests. As of this writing, we don't have a way to push a generated jar to a central repository. 

To build the jar (Java Archive) file that will be used by the other project.


```./gradlew clean assemble```

The jar file will be generated in the build/libs directory and will be named
```bot-testing-framework-<version>.jar```

To reference this in your project specific library copy the file to a lib directory in your project and reference it from your build.gradle file

```testCompile files('lib/bot-testing-framework-1.3.jar')```

See the IronHub project for an implementation of this https://github.com/menome/67-ironhub




### Jenkins

Make sure that the user Jenkins is running as (usually jenkins) has a valid docker login, and the permissions necessary to pull containers.
