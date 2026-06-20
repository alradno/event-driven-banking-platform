pipeline {
  agent any

  tools {
    jdk 'jdk-21'
  }

  stages {
    stage('Local CI Parity') {
      steps {
        sh 'make ci-local'
      }
    }
  }
}
