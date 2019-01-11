#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# To get the classpath by maven:
mvn -pl matsim dependency:build-classpath -Dmdep.outputFile=matsim.cp
classpath="\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar:\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT.jar:\
$(cat $script_dir/matsim/matsim.cp)"

echo "============CLASSPATH============"
echo $classpath
echo "======RUN ALL THE THINGS========="

#config=$script_dir/scenarios/berlin-v5.1-1pct/input/berlin-v5.1-1pct-1it.config.xml

config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-fullpt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct/input/berlin-v5.1.config.xml

# Full compilation
#mvn package -DskipTests -Denv.MPI_JAR_PATH=/usr/local/lib/mpi.jar

mvn \
    -T 4 package \
    -pl matsim \
    -am \
    -Dmaven.javadoc.skip \
    -Dsource.skip \
    -Dassembly.skipAssembly=true \
    -DskipTests #-Denv.MPI_JAR_PATH=/usr/local/lib/mpi.jar

echo "Running matsim..."
java \
	-Xmx15G \
	-Dfile.encoding=UTF-8 \
	-classpath "${classpath}" \
	org.matsim.berlin.RunBerlinScenario $config &> run.log
paplay /usr/share/sounds/freedesktop/stereo/complete.oga
echo "Running matsim...Done!"
