# 빌드 단계에서만 JDK와 메이븐 캐시를 쓰고, 실행 이미지에는 JRE와 jar만 남긴다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# pom만 먼저 넣어 의존성 레이어를 캐시한다. src만 바뀌면 재다운로드하지 않는다.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
# 테스트는 CI에서 돈다. 이미지 빌드마다 다시 돌릴 이유가 없다.
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
