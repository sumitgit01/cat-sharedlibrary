def call(Map config = [:]) {

    def appType        = config.get('appType', 'node')   // node | maven
    def sonarProjectKey= config.get('sonarProjectKey', 'default-key')
    def sonarSources   = config.get('sonarSources', 'src')
    
    pipeline {
        agent any

        tools {
            nodejs 'node20'
            maven 'maven3'   // configure in Jenkins
        }

        environment {
            SONARQUBE_SERVER = 'SonarQube'
        }

        stages {
/* 
              stage('Initialise') {
                steps {
                    cleanWs()
                }
            } */
 
            stage('Build Provisioning') {
                steps {
                   script {
                        if (appType == 'node') {
                            sh '''
                            npm install
                            npm run build
                            
                            '''
                        }
                        else if (appType == 'maven') {
                            sh '''
                            mvn clean install -DskipTests
                            '''
                        }
                        else {
                            error "Unsupported appType: ${appType}"
                        }
                    }
                }
            }
            stage('Test') {
                steps {
                    script {
                    if (appType == 'node') {
                        sh '''
                        npm test -- --coverage
                        '''
                    } else if (appType == 'maven') {
                        sh '''
                        mvn test
                        '''
                    }
                }
            }
        }
            stage('SonarQube Analysis') {
                steps {
                    withSonarQubeEnv("${SONARQUBE_SERVER}") {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {

                            script {
                                if (appType == 'node') {
                                def scannerHome    = tool 'sonar-scanner'
                                    sh """
                                    ${scannerHome}/bin/sonar-scanner \
                                    -Dsonar.projectKey=${sonarProjectKey} \
                                    -Dsonar.sources=${sonarSources} \
                                    -Dsonar.host.url=$SONAR_HOST_URL \
                                    -Dsonar.login=$SONAR_TOKEN
                                    """
                                }

                                else if (appType == 'maven') {

                                    sh """
                                    mvn sonar:sonar \
                                    -Dsonar.projectKey=${sonarProjectKey} \
                                    -Dsonar.host.url=$SONAR_HOST_URL \
                                    -Dsonar.login=$SONAR_TOKEN
                                    """
                                }
                            }
                        }
                    }
                    
                }
            }
            stage('Quality Gate') {
                steps {
                    timeout(time: 10, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
            stage('Upload images to Nexus Artifactory') {
                steps {
                    script {
                        // Using withCredentials to securely handle login
                        withCredentials([usernamePassword(
                            credentialsId: 'nexus_cred',
                            usernameVariable: 'NEXUS_USER',
                            passwordVariable: 'NEXUS_PASS'
                        )]) {
                            sh '''
                            # 1. Login using stdin to avoid password exposure in logs
                            echo "$NEXUS_PASS" | docker login 192.168.68.124:8082 -u "$NEXUS_USER" --password-stdin
                            
                            # 2. Execute the build and push script
                            chmod +x build.sh
                            ./build.sh

                            # 3. Logout and cleanup local images to save space on Jenkins agent
                            docker logout 192.168.68.124:8082
                            
                            # Optional: Cleanup local tags to keep the agent clean
                            app_name=$(jq -r ".name" package.json)
                            version=$(jq -r ".version" package.json)
                            docker rmi "192.168.68.124:8082/$app_name:$version" || true
                            '''
                        }
                    }
                }
            }
        }
    }
}

