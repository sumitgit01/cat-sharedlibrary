pipeline{
    agent {
        label params.DEPLOY_SERVER   //with read from jenkins
        } //sitserver details
    stages{
        stage('download Helm') {
            steps{
                script {
                    echo "Deploy to k8s"
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