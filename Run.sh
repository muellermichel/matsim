#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

classpath="${script_dir}/matsim/target/matsim-0.11.0-SNAPSHOT.jar:\
${script_dir}/matsim/target/matsim-0.11.0-SNAPSHOT-tests.jar:\
${HOME}/.m2/repository/log4j/log4j/1.2.15/log4j-1.2.15.jar:\
${HOME}/.m2/repository/org/geotools/gt-main/14.0/gt-main-14.0.jar:\
${HOME}/.m2/repository/org/geotools/gt-api/14.0/gt-api-14.0.jar:\
${HOME}/.m2/repository/com/vividsolutions/jts/1.13/jts-1.13.jar:\
${HOME}/.m2/repository/org/jdom/jdom/1.1.3/jdom-1.1.3.jar:\
${HOME}/.m2/repository/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar:\
${HOME}/.m2/repository/org/geotools/gt-referencing/14.0/gt-referencing-14.0.jar:\
${HOME}/.m2/repository/com/googlecode/efficient-java-matrix-library/core/0.26/core-0.26.jar:\
${HOME}/.m2/repository/commons-pool/commons-pool/1.5.4/commons-pool-1.5.4.jar:\
${HOME}/.m2/repository/org/geotools/gt-metadata/14.0/gt-metadata-14.0.jar:\
${HOME}/.m2/repository/org/geotools/gt-opengis/14.0/gt-opengis-14.0.jar:\
${HOME}/.m2/repository/net/java/dev/jsr-275/jsr-275/1.0-beta-2/jsr-275-1.0-beta-2.jar:\
${HOME}/.m2/repository/jgridshift/jgridshift/1.0/jgridshift-1.0.jar:\
${HOME}/.m2/repository/net/sf/geographiclib/GeographicLib-Java/1.44/GeographicLib-Java-1.44.jar:\
${HOME}/.m2/repository/org/geotools/gt-shapefile/14.0/gt-shapefile-14.0.jar:\
${HOME}/.m2/repository/org/geotools/gt-data/14.0/gt-data-14.0.jar:\
${HOME}/.m2/repository/org/geotools/gt-epsg-hsql/14.0/gt-epsg-hsql-14.0.jar:\
${HOME}/.m2/repository/org/hsqldb/hsqldb/2.3.0/hsqldb-2.3.0.jar:\
${HOME}/.m2/repository/org/jfree/jfreechart/1.0.19/jfreechart-1.0.19.jar:\
${HOME}/.m2/repository/org/jfree/jcommon/1.0.23/jcommon-1.0.23.jar:\
${HOME}/.m2/repository/com/google/inject/guice/4.1.0/guice-4.1.0.jar:\
${HOME}/.m2/repository/javax/inject/javax.inject/1/javax.inject-1.jar:\
${HOME}/.m2/repository/aopalliance/aopalliance/1.0/aopalliance-1.0.jar:\
${HOME}/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar:\
${HOME}/.m2/repository/com/google/inject/extensions/guice-multibindings/4.1.0/guice-multibindings-4.1.0.jar:\
${HOME}/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar:\
${HOME}/.m2/repository/org/jvnet/ogc/kml-v_2_2_0/2.2.0/kml-v_2_2_0-2.2.0.jar:\
${HOME}/.m2/repository/org/hisrc/w3c/atom-v_1_0/1.1.0/atom-v_1_0-1.1.0.jar:\
${HOME}/.m2/repository/com/sun/xml/bind/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1.jar:\
${HOME}/.m2/repository/com/sun/xml/bind/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1-sources.jar:\
${HOME}/.m2/repository/com/sun/xml/bind/jaxb-impl/2.3.0.1/jaxb-impl-2.3.0.1-sources.jar:\
${HOME}/.m2/repository/com/sun/xml/bind/jaxb-impl/2.3.0.1/jaxb-impl-2.3.0.1.jar:\
${HOME}/.m2/repository/net/jpountz/lz4/lz4/1.3.0/lz4-1.3.0.jar:\
${HOME}/.m2/repository/com/github/SchweizerischeBundesbahnen/matsim-sbb-extensions/0.10.0/matsim-sbb-extensions-0.10.0.jar:\
${HOME}/.m2/repository/org/matsim/contrib/common/0.11.0-SNAPSHOT/common-0.11.0-20180817.090538-156.jar:\
${HOME}/.m2/repository/org/apache/commons/commons-math/2.2/commons-math-2.2.jar:\
${HOME}/.m2/repository/commons-lang/commons-lang/2.3/commons-lang-2.3.jar:\
${HOME}/.m2/repository/org/matsim/contrib/analysis/0.11.0-SNAPSHOT/analysis-0.11.0-20180817.090538-156.jar:\
${HOME}/.m2/repository/org/matsim/contrib/roadpricing/0.11.0-SNAPSHOT/roadpricing-0.11.0-20180817.090538-156.jar:\
${HOME}/.m2/repository/org/osgeo/proj4j/0.1.0/proj4j-0.1.0.jar:\
${HOME}/.m2/repository/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar"

echo "============CLASSPATH============"
echo "${classpath}"
echo "======RUN ALL THE THINGS========="

#config=$script_dir/scenarios/berlin-v5.1-1pct/input/berlin-v5.1-1pct-1it.config.xml
config=$script_dir/examples/scenarios/berlin-v5.1-10pct-1agent/input/berlin-v5.1-10pct-1agent.config.xml

java \
	-Xmx10G \
	-Dfile.encoding=UTF-8 \
	-classpath "${classpath}" \
	org.matsim.berlin.RunBerlinScenario $config
