#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
java=$JAVA_HOME/bin/java

# To get the classpath by maven:
mvn -pl matsim dependency:build-classpath -Dmdep.outputFile=matsim.cp
classpath="\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar:\
$script_dir/matsim/target/matsim-0.11.0-SNAPSHOT.jar:\
$(cat $script_dir/matsim/matsim.cp)"

#config=$script_dir/scenarios/berlin-v5.1-1pct/input/berlin-v5.1-1pct-1it.config.xml

# How to test:
# 1) turn on debug (Realm.java)
# 2) cat run.log | grep "Processed 1" | wc -l

# Should yield 102 network interactions
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1agent/input/berlin-v5.1.config.xml
# Should yield 36 network interactions
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-1pt-1agent/input/berlin-v5.1.config.xml
#config=$script_dir/examples/scenarios/berlin-v5.1-1pct-fullpt/input/berlin-v5.1.config.xml
config=$script_dir/examples/scenarios/berlin-v5.1-1pct/input/berlin-v5.1.config.xml

# Overriding configs. Used to specify the number of sim threads
over_prefix=$script_dir/examples/scenarios/berlin-v5.1-1pct/input

function run {
    echo "Running matsim..."
    $java \
    	-Xmx15G \
    	-Dfile.encoding=UTF-8 \
    	-classpath "${classpath}" \
    	org.matsim.berlin.RunBerlinScenario $config $over &> run.log
    echo "Running matsim...Done!"
    grep "ETHZ" run.log | grep "Done"
}

echo "Config = $config"
#for over_suffix in 1-sim-threads 2-sim-threads 4-sim-threads 8-sim-threads
for over_suffix in 1-sim-threads
do
    over=$over_prefix/$over_suffix.xml
    echo "Over = $over"
    run
done
paplay /usr/share/sounds/freedesktop/stereo/complete.oga
beep
