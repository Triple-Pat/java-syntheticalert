#!/usr/bin/env bash
# Resolves the library from the staging repository that
# publishAllPublicationsToStagingRepository just wrote, the way a user resolves
# it from Maven Central, and runs it from empty projects: Maven (which reads
# the POM) and Gradle (which reads the Gradle module file), each on the
# classpath and on the module path. The staged files are what a release
# uploads, so this proves the upload, not the build tree.
#
# Usage: check-consumers.sh STAGING_REPO GRADLEW
#   STAGING_REPO  absolute path to build/staging-repo
#   GRADLEW       absolute path to the repository's gradlew
set -euxo pipefail

staging=$1
gradlew=$2

# Exactly one version is staged; a stale second one would make the check
# ambiguous and the release upload wrong.
versions=$(find "${staging}/com/triplepat/syntheticalert" -mindepth 1 -maxdepth 1 -type d -exec basename {} \;)
if [ "$(echo "${versions}" | wc -l)" -ne 1 ]; then
  echo "expected one staged version, found: ${versions}" >&2
  exit 1
fi
version=${versions}

work=$(mktemp -d)

# The same two programs serve both tools: a classpath Main in the unnamed
# module, and a named module that requires the library's.
mkdir -p "${work}/src/classpath" "${work}/src/modular/consumer"
cat > "${work}/src/classpath/Main.java" <<'EOF'
import com.triplepat.syntheticalert.SyntheticAlert;

public class Main {
  public static void main(String[] args) {
    System.out.println("classpath " + SyntheticAlert.create().value());
  }
}
EOF
cat > "${work}/src/modular/module-info.java" <<'EOF'
module consumer {
  requires com.triplepat.syntheticalert;
}
EOF
cat > "${work}/src/modular/consumer/Main.java" <<'EOF'
package consumer;

import com.triplepat.syntheticalert.SyntheticAlert;

public class Main {
  public static void main(String[] args) {
    System.out.println("module path " + SyntheticAlert.create().value());
  }
}
EOF

# --- Maven ---
# A fresh local repository, so nothing resolves from an earlier run, and the
# staging repository ahead of Central, which Maven always tries last.
mkdir -p "${work}/maven"
cat > "${work}/maven/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>consumer</groupId>
  <artifactId>consumer</artifactId>
  <version>1</version>
  <repositories>
    <repository>
      <id>staging</id>
      <url>file://${staging}</url>
    </repository>
  </repositories>
  <dependencies>
    <dependency>
      <groupId>com.triplepat</groupId>
      <artifactId>syntheticalert</artifactId>
      <version>${version}</version>
    </dependency>
  </dependencies>
</project>
EOF
m2="${work}/m2"
mvn -B -q -f "${work}/maven/pom.xml" -Dmaven.repo.local="${m2}" \
  dependency:build-classpath -Dmdep.outputFile="${work}/maven/cp.txt"
# Once a version is on Central, Central could serve it too; insist that what
# Maven resolved is byte for byte what was staged. (Maven's own record of the
# source repository, _remote.repositories, changes format between releases.)
for f in "syntheticalert-${version}.jar" "syntheticalert-${version}.pom"; do
  cmp "${staging}/com/triplepat/syntheticalert/${version}/${f}" \
    "${m2}/com/triplepat/syntheticalert/${version}/${f}"
done
cp=$(cat "${work}/maven/cp.txt")

javac -d "${work}/maven/classpath" -cp "${cp}" "${work}/src/classpath/Main.java"
java -cp "${work}/maven/classpath:${cp}" Main

javac -d "${work}/maven/modular" -p "${cp}" \
  "${work}/src/modular/module-info.java" "${work}/src/modular/consumer/Main.java"
java -p "${work}/maven/modular:${cp}" -m consumer/consumer.Main

# --- Gradle ---
# The staging repository is the only one allowed to serve com.triplepat, for
# the same reason as the Maven check above. A module-info.java in the source
# set puts the dependencies on the module path.
gradle_consumer() {
  local name=$1 main=$2
  local dir="${work}/gradle-${name}"
  mkdir -p "${dir}/src/main"
  cp -R "${work}/src/${name}" "${dir}/src/main/java"
  echo "rootProject.name = \"consumer\"" > "${dir}/settings.gradle.kts"
  cat > "${dir}/build.gradle.kts" <<EOF
plugins { application }

repositories {
  exclusiveContent {
    forRepository { maven { url = uri("file://${staging}") } }
    filter { includeGroup("com.triplepat") }
  }
  mavenCentral()
}

dependencies { implementation("com.triplepat:syntheticalert:${version}") }

application { ${main} }
EOF
  "${gradlew}" -q -p "${dir}" --no-build-cache run
}
gradle_consumer classpath 'mainClass.set("Main")'
gradle_consumer modular 'mainModule.set("consumer"); mainClass.set("consumer.Main")'
