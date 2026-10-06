# Build reproducible: el perfil "production" de Maven ya orquesta todo el
# frontend (instala Node, compila el bundle de Vaadin y el build de player-app),
# asi que esta etapa solo necesita Maven + JDK y salida a internet.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
COPY player-app player-app
COPY gym-app gym-app
RUN mvn -B package -Pproduction -DskipTests

# Runtime liviano: solo el JRE y el jar ya armado. Los tests (que levantan
# Postgres embebido) corren aparte con "mvn test", no tiene sentido repetirlos
# en cada build de imagen.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/padel-saas-*.jar app.jar
EXPOSE 8080

# Render da 512MB y Java por defecto solo acota el heap (25%, 128MB): metaspace,
# code cache y la memoria nativa crecian sin techo hasta que Render mataba la
# instancia sin dejar nada en el log. Con estos topes el proceso entra con margen.
# - TieredStopAtLevel=1: sin el compilador C2. El codigo mas usado corre algo mas
#   lento, pero la app espera a la base casi todo el tiempo y con poca CPU
#   compilar menos incluso ayuda.
# - Metaspace y code cache con techo: si alguno se pasa, sale el error en el log
#   en vez de un reinicio mudo.
# - ExitOnOutOfMemoryError: sin heap la app sale y Render la levanta limpia, en
#   vez de seguir andando a medias.
# - MALLOC_ARENA_MAX: glibc abre una arena de memoria por hilo y en contenedores
#   eso se infla con los dias sin devolverse nunca.
ENV MALLOC_ARENA_MAX=2
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:MaxMetaspaceSize=192m", "-XX:ReservedCodeCacheSize=96m", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
