def call() {
pipeline {
    agent any
    stages{
        stage('Build'){
            steps{
                echo "Building Nodejs Application"
                }
            }
        }
    }
}