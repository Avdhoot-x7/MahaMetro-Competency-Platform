#!/bin/sh
# Tomcat 11 Environment & JVM Performance Tuning for Maha Metro Platform
# File location: /opt/tomcat11/bin/setenv.sh

export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export CATALINA_PID="/opt/tomcat11/temp/tomcat.pid"

# JVM Memory and Garbage Collection Tuning (G1GC for sub-second pause times)
export CATALINA_OPTS="\
  -Xms4g \
  -Xmx8g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ParallelRefProcEnabled \
  -XX:+AlwaysPreTouch \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=UTC \
  -Dspring.profiles.active=prod"

# Ensure umask restricts file permissions to tomcat user
umask 0027
