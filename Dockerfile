FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

RUN mkdir -p /workspace/target/dependency && (cd /workspace/target/dependency && jar -xf /workspace/target/monolit-0.0.1-SNAPSHOT.jar)

FROM eclipse-temurin:21-jdk-alpine AS jre-build
WORKDIR /workspace

COPY --from=build /workspace/target/dependency ./dependency
RUN $JAVA_HOME/bin/jdeps \
    --ignore-missing-deps \
    --multi-release 21 \
    --recursive \
    --print-module-deps \
    --class-path 'dependency/BOOT-INF/lib/*' \
    dependency/BOOT-INF/classes > /tmp/modules.txt
RUN $JAVA_HOME/bin/jlink \
    --add-modules $(cat /tmp/modules.txt),jdk.crypto.ec \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output /opt/jre

FROM alpine:3.21 AS runtime
WORKDIR /app

RUN apk add --no-cache ca-certificates libstdc++
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=jre-build /opt/jre /opt/jre
COPY --from=build /workspace/target/monolit-0.0.1-SNAPSHOT.jar /app/app.jar

ENV MONOLIT_TARGET_HOST="0.0.0.0"
ENV MONOLIT_TARGET_PORT="1234"
ENV VK_GROUP_TOKEN="tokentoken"
ENV VK_GROUP_ID="1"
ENV VK_MY_ID="2"
ENV PATH="/opt/jre/bin:${PATH}"

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
