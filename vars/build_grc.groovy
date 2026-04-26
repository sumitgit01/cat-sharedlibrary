pipeline {
    agent any

    environment {
        OPENPAGES_URL = "https://openpages/api/v1/risks"
        TOKEN = credentials('openpages-token')
    }

    stages { 

        stage('Build') {
            steps {
                sh 'mvn clean install'
            }
        }

        stage('Sonar Scan') {
            steps {
                sh 'mvn sonar:sonar'
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    def qg = waitForQualityGate()
                    if (qg.status != 'OK') {
                        env.QUALITY_STATUS = "FAILED"
                    } else {
                        env.QUALITY_STATUS = "PASSED"
                    }
                }
            }
        }

        stage('Security Scan') {
            steps {
                script {
                    // Example: fetch vulnerability count
                    env.CRITICAL = "1"
                    env.HIGH = "3"
                }
            }
        }

        stage('Risk Evaluation') {
            steps {
                script {
                    if (env.CRITICAL.toInteger() > 0) {
                        env.RISK = "HIGH"
                        env.CONTROL = "NON_COMPLIANT"
                    } else if (env.HIGH.toInteger() > 5) {
                        env.RISK = "MEDIUM"
                        env.CONTROL = "PARTIAL"
                    } else {
                        env.RISK = "LOW"
                        env.CONTROL = "COMPLIANT"
                    }
                }
            }
        }

        stage('Update OpenPages') {
            steps {
                script {
                    def payload = """
                    {
                      "app": "payment-service",
                      "build": "${BUILD_NUMBER}",
                      "risk": "${env.RISK}",
                      "control": "${env.CONTROL}",
                      "quality": "${env.QUALITY_STATUS}"
                    }
                    """

                    sh """
                    curl -X POST ${OPENPAGES_URL} \
                    -H "Authorization: Bearer ${TOKEN}" \
                    -H "Content-Type: application/json" \
                    -d '${payload}'
                    """
                }
            }
        }

        stage('Deployment Gate') {
            steps {
                script {
                    if (env.RISK == "HIGH") {
                        error("Deployment blocked due to HIGH risk")
                    }
                }
            }
        }
    }
}