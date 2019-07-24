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

# Comment if you want to build the whole MATSim
projects="-pl matsim"

mvn \
    -T 4 package \
    $projects \
    -am \
    -Dmaven.javadoc.skip \
    -Dassembly.skipAssembly=true \
    -DskipTests

# install jar in maven local repo, didn't work very well through mvn.
matsimmaven=~/.m2/repository/org/matsim/matsim/11.0
cp matsim/target/*.jar $matsimmaven
cd $prev_dir
