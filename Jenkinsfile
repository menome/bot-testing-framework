pipeline {
  agent any
  stages {
    stage('test') {
      steps {
        sh './gradlew :cleanTest :test --tests "com.menome.test.*"'
      }
    }
  }
}