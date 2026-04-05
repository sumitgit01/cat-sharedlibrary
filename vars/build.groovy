    def call(Map config = [:]) {

        def appType        = config.get('appType', 'node')   // node | maven
        def sonarProjectKey= config.get('sonarProjectKey', 'default-key')
        def sonarSources   = config.get('sonarSources', 'src')
        // Get the port from Jenkinsfile, default to 8082 if not provided
        def nexusPort       = config.get('nexusPort', '8082')
        def repoName  = config.get('repoName', 'default-repo')
        def helmRepoName = config.get('helmRepoName')
        def helmPort = config.get('helmPort')
        pipeline {
            agent any

            tools {
                nodejs 'node20'
                maven 'maven3'   // configure in Jenkins
                jdk 'jdk21'
            }

            environment {
                SONARQUBE_SERVER = 'SonarQube'
                NEXUS_IP   = "192.168.68.124"
                NEXUS_URL  = "${NEXUS_IP}:${nexusPort}"
                NEXUS_HELM_URL = "${NEXUS_IP}:${helmPort}"
                REPO_NAME  = "${repoName}"
                HELM_REPO_NAME = "${helmRepoName}"
                // DEPLOY_TARGET = "192.168.68.126"
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
                                    #export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
                                    #export PATH=$JAVA_HOME/bin:$PATH
                                    export JAVA_HOME=/opt/jdk-21.0.9
                                    #export PATH=$JAVA_HOME/bin
                                    echo $JAVA_HOME
                                    export M2_HOME=/opt/apache-maven-3.9.11
                                    export PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH
                                    mvn clean install -DskipTests
                                '''
                                def version = sh(script: "/opt/apache-maven-3.9.11/bin/mvn help:evaluate -Dexpression=project.version -q -DforceStdout", returnStdout: true).trim()
                                env.APP_VERSION = version
                                print("APP VERSION is "+APP_VERSION)
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
                                        export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
                                        export PATH=$JAVA_HOME/bin:$PATH
                                        echo $JAVA_HOME
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
                stage('Package & Upload Helm Chart') {
                    steps {
                        script {
                            withCredentials([usernamePassword(
                                credentialsId: 'nexus_cred',
                                usernameVariable: 'NEXUS_USER',
                                passwordVariable: 'NEXUS_PASS'
                            )]) {

                                sh '''
                                    export PATH=$PATH:/usr/local/bin

                                    cd manifestbuild

                                    CHART_NAME=$(grep '^name:' Chart.yaml | awk '{print $2}')
                                    CHART_VERSION=$(grep '^version:' Chart.yaml | awk '{print $2}')

                                    echo "Chart Name: $CHART_NAME"
                                    echo "Chart Version: $CHART_VERSION"

                                    helm package .

                                    PACKAGE_NAME="${CHART_NAME}-${CHART_VERSION}.tgz"

                                    echo "Uploading $PACKAGE_NAME to Nexus"

                                    curl -u "$NEXUS_USER:$NEXUS_PASS" \
                                    -X POST "http://${NEXUS_HELM_URL}/service/rest/v1/components?repository=cat-helm" \
                                    -H "accept: application/json" \
                                    -H "Content-Type: multipart/form-data" \
                                    -F "helm.asset=@$PACKAGE_NAME"
                                    '''
                            }
                        }
                    }
                }
            }
        }
    }

