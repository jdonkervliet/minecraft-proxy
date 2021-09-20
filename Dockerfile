FROM openjdk:8-jre-alpine
COPY target/mcproxy-1.0-SNAPSHOT.jar /mcproxy.jar
EXPOSE 25566
CMD ["java", "-jar", "/mcproxy.jar"]
