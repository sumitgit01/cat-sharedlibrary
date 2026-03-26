def call() {
    stage('Build Backend') {
        sh 'mvn clean package -DskipTests'
    }
}