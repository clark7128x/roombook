FROM eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269
WORKDIR /app
ARG JAR_FILE=target/roombook-0.0.1-SNAPSHOT.jar
COPY --chown=10001:10001 ${JAR_FILE} /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -Duser.timezone=UTC"
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
