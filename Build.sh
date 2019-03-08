#!/bin/bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

prev_dir=$(pwd)

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd "${script_dir}"

mvn clean

rm -r examples/scenarios/berlin-v5.1-1pct*/output/* &> /dev/null

mvn \
    -T 4 package \
    -pl matsim \
    -am \
    -Dmaven.javadoc.skip \
    -Dsource.skip \
    -Dassembly.skipAssembly=true \
    -DskipTests

cd $prev_dir
