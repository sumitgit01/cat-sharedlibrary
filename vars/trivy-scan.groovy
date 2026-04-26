pipeline {
    agent {
        //Docker Pipeline pugin must be installed in Jenkins for this to work
        docker {
            image 'summitjoshi/jenkins-trivy-agent:v1'
            args '--user root -v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        IMAGE_NAME = 'summitjoshi/cat-frontend'
        IMAGE_TAG = '0.1.0'
        FULL_IMAGE = "${IMAGE_NAME}:${IMAGE_TAG}"
    }

    stages {
        stage('Pull Image') {
            steps {
                sh '''
                    echo "Pulling image: $FULL_IMAGE"
                    docker pull $FULL_IMAGE
                '''
            }
        }

        stage('Trivy Scan & Report') {
            steps {
                sh '''
                trivy image --format table -o trivy-report.txt $FULL_IMAGE
                '''
            }
        }

        stage('Email Report') {
            steps {
                emailext (
                    subject: "Trivy Scan Report - ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: """
Trivy scan completed.

Image: ${env.FULL_IMAGE}

Check attached report.
""",
                    to: "sumitjoshi1988@gmail.com",
                    attachmentsPattern: "trivy-report.txt"
                )
            }
        }
    }
}