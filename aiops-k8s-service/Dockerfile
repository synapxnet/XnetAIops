FROM eclipse-temurin:17-jdk-slim
WORKDIR /app
COPY target/aiops-k8s-service-1.3.0.jar app.jar
EXPOSE 9186
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -jar app.jar"]
