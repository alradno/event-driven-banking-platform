pipeline {
  agent any

  tools {
    jdk 'jdk-21'
  }

  stages {
    stage('Test') {
      steps {
        sh './mvnw test'
        sh 'python3 -m unittest discover -s ai-incident-assistant/tests'
      }
    }
    stage('Build Images') {
      steps {
        sh 'podman compose build'
      }
    }
    stage('Smoke') {
      steps {
        sh 'podman compose up -d'
        sh './scripts/smoke-test.sh'
      }
      post {
        always {
          sh 'podman compose logs --tail=200 || true'
          sh 'podman compose down -v || true'
        }
      }
    }
  }
}
