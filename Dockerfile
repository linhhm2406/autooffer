# Sử dụng Maven + JDK 21 làm base để build
FROM maven:3.9.4-eclipse-temurin-21 AS build

# Thư mục làm việc
WORKDIR /app

# Copy file pom.xml và tải dependencies offline
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ source code
COPY src ./src

# Build project, bỏ qua tests
RUN mvn clean package -DskipTests

# ---- Stage chạy ứng dụng ----
FROM eclipse-temurin:21-jdk

# Thư mục làm việc
WORKDIR /app

# Copy jar từ stage build
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# CMD chạy Spring Boot
CMD ["java", "-jar", "app.jar"]
