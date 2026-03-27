pipeline {
    agent any
    stages{
        stage('Build'){
            steps{
                echo "BuildingNodejs Application"
            }
        }
    }
}