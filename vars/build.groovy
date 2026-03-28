def call(Map config = [:]) {

    def appType        = config.get('appType', 'node')   // node | maven
    def appDir         = config.get('appDir', '.')
    def sonarProjectKey= config.get('sonarProjectKey', 'default-key')
    def sonarSources   = config.get('sonarSources', 'src')
    
    pipeline {
        agent any

        tools {
            nodejs 'node20'
            maven 'maven3'   // configure in Jenkins
        }

        environment {
            SONARQUBE_SERVER = 'SonarQube'
        }

        stages {

            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Build') {
                steps {
                    dir(appDir) {

                        script {
                            if (appType == 'node') {
                                sh '''
                                npm install
                                npm run build
                                '''
                            }

                            else if (appType == 'maven') {
                                sh '''
                                mvn clean install -DskipTests
                                '''
                            }

                            else {
                                error "Unsupported appType: ${appType}"
                            }
                        }
                    }
                }
            }

            stage('SonarQube Analysis') {
                steps {
                    dir(appDir) {

                        withSonarQubeEnv("${SONARQUBE_SERVER}") {

                            withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {

                                script {
                                    if (appType == 'node') {
                                    def scannerHome    = tool 'sonar-scanner'
                                        sh """
                                        ${scannerHome}/bin/sonar-scanner \
                                        -Dsonar.projectKey=${sonarProjectKey} \
                                        -Dsonar.sources=${sonarSources} \
                                        -Dsonar.host.url=$SONAR_HOST_URL \
                                        -Dsonar.login=$SONAR_TOKEN
                                        """
                                    }

                                    else if (appType == 'maven') {

                                        sh """
                                        mvn sonar:sonar \
                                        -Dsonar.projectKey=${sonarProjectKey} \
                                        -Dsonar.host.url=$SONAR_HOST_URL \
                                        -Dsonar.login=$SONAR_TOKEN
                                        """
                                    }
                                }
                            }
                        }
                    }
                }
            }

            stage('Quality Gate') {
                steps {
                    timeout(time: 3, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
        }
    }
}