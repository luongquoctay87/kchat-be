# Build: docker build -t kchat-api .
# Run (prod image — all secrets required):
#   docker run --rm -p 8864:8864 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... \
#     -e REDIS_HOST=... -e REDIS_PASSWORD=... \
#     -e JWT_ACCESS_SECRET=... \
#     -e KCHAT_OPS_ALERTS_WEBHOOK_SECRET=... \
#     -e SMTP_HOST=... -e SMTP_USER=... -e SMTP_PASSWORD=... \
#     -e KCHAT_S3_BUCKET=... -e KCHAT_S3_REGION=ap-southeast-1 \
#     kchat-api
# ECS: omit KCHAT_S3_ACCESS_KEY — uses the task role.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S kchat && adduser -S kchat -G kchat
WORKDIR /app
COPY --from=build /workspace/target/kchat-api.jar app.jar
RUN chown kchat:kchat app.jar
USER kchat
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8864
ENTRYPOINT ["java", "-jar", "app.jar"]
