# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -Dmaven.test.skip=true

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 ems
COPY --from=build /app/target/employee-management-system-1.0.0.jar app.jar
USER ems

# Sized for small containers (e.g. Render free tier, 512 MB)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
