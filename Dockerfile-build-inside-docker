FROM gradle:8.4.0-jdk21 AS builder

ARG ACTIVE_PROFILES
ARG PROJECT_NAME

RUN mkdir /coin-trader
COPY . /coin-trader
RUN echo "spring.profiles.active=${ACTIVE_PROFILES}" > /coin-trader/${PROJECT_NAME}/src/main/resources/application.properties
WORKDIR /coin-trader

RUN gradle ${PROJECT_NAME}:bootJar

FROM amazoncorretto:21

WORKDIR /
COPY --from=builder /coin-trader/${PROJECT_NAME}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "/app.jar"]