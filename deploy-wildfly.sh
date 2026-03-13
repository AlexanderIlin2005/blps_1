#!/bin/bash
set -e

# Default WildFly directory if not provided via environment variable
WILDFLY_HOME=${WILDFLY_HOME:-/opt/wildfly}
DEPLOY_DIR="${WILDFLY_HOME}/standalone/deployments"

echo "Building WAR file for deployment..."
chmod +x gradlew
./gradlew clean build -x test

# Extract the war filename
WAR_FILE="build/libs/app.war"

if [ ! -f "$WAR_FILE" ]; then
    echo "Build failed: WAR file not found!"
    exit 1
fi

echo "Deploying to WildFly at ${DEPLOY_DIR}..."

# Stop previous deployment
rm -f "${DEPLOY_DIR}/app.war.deployed"
rm -f "${DEPLOY_DIR}/app.war.failed"

# Copy the new WAR
cp "$WAR_FILE" "${DEPLOY_DIR}/app.war"

# Trigger deployment
touch "${DEPLOY_DIR}/app.war.dodeploy"

echo "Deployment triggered. Check WildFly logs for progress."
