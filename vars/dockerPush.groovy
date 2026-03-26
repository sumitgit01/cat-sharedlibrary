def call(Map config) {
    stage('Docker Build & Push') {
        sh "docker build -t ${config.image}:${config.tag} ."
        sh "docker push ${config.image}:${config.tag}"
    }
}