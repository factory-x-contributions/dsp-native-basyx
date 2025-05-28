FROM eclipse-temurin:21.0.5_11-jre-alpine
WORKDIR /app
COPY target/BasyxStarterApplication-0.0.1-SNAPSHOT.jar /app/app.jar
COPY src/main/resources/rules.json /app/rules.json

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]