FROM eclipse-temurin:21-jdk
VOLUME /tmp
# Maven 빌드 결과물인 jar 파일을 복사
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]