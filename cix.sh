#!/bin/bash
# QA pipeline

set -euo pipefail

case "$RUN_ACTIVITY" in

  run-db-unit-tests-*)
    DB_ENGINE=$(sed "s/run-db-unit-tests-//g" <<< $RUN_ACTIVITY)
    ./run-db-unit-tests.sh "http://infra.internal.sonarsource.com/jenkins/orch-${DB_ENGINE}.properties"  \
        -PbuildProfile=sonarsource-qa \
        -DbuildNumber=$CI_BUILD_NUMBER
    ;;

  run-db-integration-tests-*)
    DB_ENGINE=$(sed "s/run-db-integration-tests-//g" <<< $RUN_ACTIVITY | cut -d \- -f 1)
    CATEGORY=$(sed "s/run-db-integration-tests-//g" <<< $RUN_ACTIVITY | cut -d \- -f 2)

    if [[ "$GITHUB_BRANCH" != "PULLREQUEST-"* ]] && [[ "$GITHUB_BRANCH" != "master" ]] && [[ "$GITHUB_BRANCH" != "branch-"* ]] && [[ "$GITHUB_BRANCH" != "dogfood-on-next" ]]; then
      # do not execute QA tests on feature branch outside pull request
      exit 0

    elif [[ "$GITHUB_BRANCH" == "PULLREQUEST-"* ]] && [[ "$DB_ENGINE" != "postgresql93" ]]; then
      # restrict QA tests to PostgreSQL on pull requests
      exit 0

    elif [[ "$GITHUB_BRANCH" == "dogfood-on-next" ]] && [[ "$DB_ENGINE" != "postgresql93" ]]; then
      # restrict QA tests to PostgreSQL on dogfood branch
      exit 0

    else
      ./gradlew --no-daemon --console plain -i \
          :tests:integrationTest \
          -Dcategory="$CATEGORY" \
          -Dorchestrator.configUrl="http://infra.internal.sonarsource.com/jenkins/orch-$DB_ENGINE.properties" \
          -PbuildProfile=sonarsource-qa \
          -DbuildNumber=$CI_BUILD_NUMBER \
          -Drepox-api-key=$REPOX_API_KEY
    fi
    ;;

  run-it-released-plugins)
    ./run-integration-tests.sh "Plugins" "http://infra.internal.sonarsource.com/jenkins/orch-h2.properties" \
          -PbuildProfile=sonarsource-qa \
          -DbuildNumber=$CI_BUILD_NUMBER \
          -Drepox-api-key=$REPOX_API_KEY
    ;;

  run-perf-tests)
      ./run-perf-tests.sh \
          -PbuildProfile=sonarsource-qa \
          -DbuildNumber=$CI_BUILD_NUMBER \
          -Drepox-api-key=$REPOX_API_KEY
    ;;

  run-upgrade-tests-*)
    DB_ENGINE=$(sed "s/run-upgrade-tests-//g" <<< $RUN_ACTIVITY)
      ./run-upgrade-tests.sh "http://infra.internal.sonarsource.com/jenkins/orch-${DB_ENGINE}.properties" \
          -PbuildProfile=sonarsource-qa \
          -DbuildNumber=$CI_BUILD_NUMBER \
          -Drepox-api-key=$REPOX_API_KEY
    ;;

  *)
    echo "unknown RUN_ACTIVITY = $RUN_ACTIVITY"
    exit 1
    ;;

esac
