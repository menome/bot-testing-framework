# Menome Bot Testing Framework

This project is used to test menome bots. It can be used locally to enable local testing as well on the CI server.

The project makes heavy use of the testcontainers project (https://www.testcontainers.org/) for spinning up docker containers in the test environment
Tests are written using the Spock (http://spockframework.org/) BDD testing framework and Groovy (http://groovy-lang.org/)

TODO:Examples
TODO:Running test locally (Docker and standalone)
TODO:Debugging hints

Make sure that the user Jenkins is running as (usually jenkins) has a valid docker login, and the permissions necessary to pull containers.
