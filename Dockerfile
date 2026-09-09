# Build reproducible: el perfil "production" de Maven ya orquesta todo el
# frontend (instala Node, compila el bundle de Vaadin y el build de player-app),
# asi que esta etapa solo necesita Maven + JDK y salida a internet.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
COPY player-app player-app
RUN mvn -B package -Pproduction -DskipTests

# Runtime liviano: solo el JRE y el jar ya armado. Los tests (que levantan
# Postgres embebido) corren aparte con "mvn test", no tiene sentido repetirlos
# en cada build de imagen.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/padel-saas-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
