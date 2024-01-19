FROM amazoncorretto:21

ARG PROJECT_NAME

WORKDIR /
COPY ./${PROJECT_NAME}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "/app.jar"]