FROM node:22-alpine AS web
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS java
WORKDIR /build
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -pl backend dependency:go-offline
COPY backend/src backend/src
COPY --from=web /build/frontend/dist/ frontend/dist/
RUN mvn -B -pl backend -Pproduction-web -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -g 10001 alphascope && adduser -D -u 10001 -G alphascope alphascope
WORKDIR /app
COPY --from=java --chown=10001:10001 /build/backend/target/alphascope-backend-0.1.0.jar /app/app.jar
RUN mkdir -p /app/.runtime && chown 10001:10001 /app/.runtime
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai"
ENTRYPOINT ["java","-Dalphascope.root=/app","-jar","/app/app.jar"]
