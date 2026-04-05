def APP_NAME = params.APP_NAME
def NEXUS_URL = "http://192.168.68.124:8081"
pipeline{
    agent {
        label params.DEPLOY_SERVER   //with read from jenkins
        } //sitserver details
    stages{
        stage('download Helm') {
            steps{
                script {
                    withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                        echo "Deploy to k8s"
                        sh '''
                            helm repo add ${APP_NAME}-helm $NEXUS_URL \
                            --username $USER --password $PASS
                            ls -alrth
                            helm repo update
                        '''
                    }
                }
            }
        }
        stage('SIT Deploy') {
            steps{
                script {
                    echo "Deploy to k8s server"
                }
            }
        }
    }
}