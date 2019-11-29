pipeline {
  agent any
  stages {
    stage('error') {
      steps {
        sh './gradlew :cleanTest :test --tests "com.menome.test.DataRefinerySpecification"'
      }
    }
  }
}