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
WORKDIR /opt/meuebitify

RUN addgroup -S meuebitify && adduser -S meuebitify -G meuebitify \
    && mkdir -p /opt/meuebitify/dados-runtime \
    && chown -R meuebitify:meuebitify /opt/meuebitify

COPY --from=build --chown=meuebitify:meuebitify /app/backend/target/itaqui-ebitda-0.1.0.jar ./meuebitify.jar

USER meuebitify

ENV APP_COOKIE_SECURE=true \
    APP_FORWARD_HEADERS=framework \
    APP_CORS_ORIGENS="" \
    APP_DADOS_DIRETORIO=/opt/meuebitify/dados-runtime

EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx320m", "-XX:+UseSerialGC", "-jar", "/opt/meuebitify/meuebitify.jar"]
