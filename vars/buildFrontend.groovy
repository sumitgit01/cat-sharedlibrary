def call() {
    stage('Build Frontend') {
        sh 'npm install'
        sh 'npm run build'
    }
}