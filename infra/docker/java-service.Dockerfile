FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN chmod +x ./mvnw && ./mvnw -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
