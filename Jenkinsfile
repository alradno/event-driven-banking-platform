pipeline {
  agent any

  tools {
    jdk 'jdk-21'
  }

  stages {
    stage('Repository Scans') {
      steps {
        sh './scripts/ci-scan.sh'
      }
    }
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
        sh './scripts/e2e-test.sh'
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
