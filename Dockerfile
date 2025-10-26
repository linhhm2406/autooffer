# Dùng OpenJDK 21
FROM eclipse-temurin:21-jdk

# Đặt thư mục làm việc
WORKDIR /app

# Copy file pom.xml và tải dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code và build
COPY src ./src
RUN mvn clean package -DskipTests

# Chạy file jar sinh ra
CMD ["java", "-jar", "target/yourappname-0.0.1-SNAPSHOT.jar"]

# Expose port 8080
EXPOSE 8080