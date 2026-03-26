def call(Map config) {
    stage('Deploy to K8s') {
        sh "kubectl apply -f ${config.manifest}"
    }
}