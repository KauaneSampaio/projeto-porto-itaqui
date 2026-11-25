# Etapa 1: Build do JAR único (back-end + front-end embutido em static/)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY backend/mvnw backend/pom.xml ./backend/
COPY backend/.mvn ./backend/.mvn
RUN chmod +x backend/mvnw

COPY frontend ./frontend
COPY backend/src ./backend/src

WORKDIR /app/backend
RUN ./mvnw -B package -DskipTests

# Etapa 2: Imagem final enxuta e segura (JRE 21 + usuário sem privilégios)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/ebitify

RUN addgroup -S ebitify && adduser -S ebitify -G ebitify \
    && mkdir -p /opt/ebitify/dados-runtime \
    && chown -R ebitify:ebitify /opt/ebitify

COPY --from=build --chown=ebitify:ebitify /app/backend/target/itaqui-ebitda-0.1.0.jar ./ebitify.jar

USER ebitify

ENV APP_COOKIE_SECURE=true \
    APP_FORWARD_HEADERS=framework \
    APP_CORS_ORIGENS="" \
    APP_DADOS_DIRETORIO=/opt/ebitify/dados-runtime

EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx320m", "-XX:+UseSerialGC", "-jar", "/opt/ebitify/ebitify.jar"]
