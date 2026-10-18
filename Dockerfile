FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
COPY brand ./brand
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system storycheck && useradd --system --gid storycheck --home-dir /app storycheck
WORKDIR /app
COPY --from=build --chown=storycheck:storycheck /workspace/target/story-check-ai.jar ./app.jar
COPY LICENSE ./LICENSE
USER storycheck
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
