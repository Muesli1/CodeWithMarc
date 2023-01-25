# Build stage
FROM openjdk:11
COPY . /app
WORKDIR /app
RUN ls -a
RUN ./gradlew server:buildFatJar

# Deploy stage
FROM openjdk:11.0-jre-slim
COPY --from=0 /app/server/build/libs/fat.jar /app/code-with-marc-server.jar
EXPOSE 8080:8080
ENTRYPOINT ["java","-jar","/app/code-with-marc-server.jar"]