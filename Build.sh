#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

prev_di=r$(pwd)

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd "${script_dir}"

# Prepare things for eclipse
# Note: check if the JVM is correct
# Note2: check if M2_REPO is correctly defined
# Note3: ln -s examples matsim_examples
# mvn eclipse:eclipse

mvn clean

sudo rm -r examples/scenarios/berlin-v5.1-1pct*/output &> /dev/null

#projects="-pl matsim"

mvn \
    -T 4 package \
    $projects \
    -am \
    -Dmaven.javadoc.skip \
    -DskipTests

# install jar in maven local repo
mvn install:install-file -Dfile=matsim/target/matsim-0.11.0-SNAPSHOT.jar
#mvn install:install-file -Dfile=matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar
#mvn install:install-file -Dfile=matsim/target/matsim-0.11.0-SNAPSHOT-sources.jar

cd $prev_dir
