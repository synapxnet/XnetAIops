FROM eclipse-temurin:17-jdk-slim
WORKDIR /app
COPY target/aiops-mon-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 9184
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -jar app.jar"]
