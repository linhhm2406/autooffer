# ---- Stage 1: Build với Maven ----
FROM maven:3.9.4-eclipse-temurin-21 AS build

# Thư mục làm việc
WORKDIR /app

# Copy file pom.xml trước để cache dependencies
COPY pom.xml .

# Tải offline dependencies (giúp build nhanh cho lần deploy tiếp theo)
RUN mvn dependency:go-offline

# Copy toàn bộ source code
COPY src ./src

# Build fat jar, bỏ qua tests
RUN mvn clean package spring-boot:repackage -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jdk

# Thư mục làm việc
WORKDIR /app

# Copy jar đã build từ stage trước
COPY --from=build /app/target/*.jar app.jar

# Expose port ứng dụng (phải trùng với internal_port trong fly.toml)
EXPOSE 8080

# Command chạy Spring Boot
CMD ["java", "-jar", "app.jar"]
