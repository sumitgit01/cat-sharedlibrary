pipeline{
    agent any //sitserver details
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