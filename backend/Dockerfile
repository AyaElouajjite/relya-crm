FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system crm
COPY --from=build /app/target/crm-backend-*.jar app.jar
USER crm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
