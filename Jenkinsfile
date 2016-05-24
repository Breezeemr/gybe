// Always omit teamDomain & token args from slackSend: these come from Jenkins
// Can also omit channel to use the default (#commits)

node {
  checkout scm
  try {
    sh "mkdir -p target/reports && /var/lib/jenkins/bin/lein with-profile +jenkins test-out junit target/reports/TEST-\$( date +%s ).xml"
    step([$class: 'JUnitResultArchiver', testResults: '**/target/reports/TEST-*.xml'])
    slackSend color: 'good', message: "Gybe tests succeeded: <${env.BUILD_URL}console|${env.BUILD_TAG}>"
  } catch (err) {
    slackSend color: 'danger', message: "Gybe tests failed: <${env.BUILD_URL}console|${env.BUILD_TAG}>"
    error err
  }
}
