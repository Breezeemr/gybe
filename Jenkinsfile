node ('master'){
  checkout scm
  slackSend channel: '#commits', color: 'green', message: "Jenkins Building ${env.BUILD_URL}console", teamDomain: 'breezeehr', token: 'lTYii0DOVv4h5oAkSfwkWrTT'
  try {
    sh "mkdir -p target/reports && /var/lib/jenkins/bin/lein with-profile +jenkins test-out junit target/reports/TEST-\$( date +%s ).xml"
    step([$class: 'JUnitResultArchiver', testResults: '**/target/reports/TEST-*.xml'])
    slackSend channel: '#commits', color: 'green', message: 'Succeeded', teamDomain: 'breezeehr', token: 'lTYii0DOVv4h5oAkSfwkWrTT'
  } catch (err) {
    slackSend channel: '#commits', color: 'red', message: "Build Failed :thumbsdown: ${env.BUILD_URL}console", teamDomain: 'breezeehr', token: 'lTYii0DOVv4h5oAkSfwkWrTT'
    error err
  }

}
