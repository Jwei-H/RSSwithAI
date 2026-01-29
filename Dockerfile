# ========== Stage 1: Build Backend (Java) ==========
FROM maven:3.9-eclipse-temurin-25 AS build-backend
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Skip tests to speed up build
RUN mvn clean package -DskipTests

# ========== Stage 2: Build User Frontend (Node) ==========
FROM node:22-alpine AS build-user
WORKDIR /app
COPY fronted-user/package*.json ./
RUN npm install
COPY fronted-user/ .
# Ensure VITE_API_BASE_URL is relative for web hosting, OR empty to default to code logic
ENV VITE_API_BASE_URL=""
RUN npm run build

# ========== Stage 3: Build Admin Frontend (Node) ==========
FROM node:22-alpine AS build-admin
WORKDIR /app
COPY fronted-admin/package*.json ./
RUN npm install
COPY fronted-admin/ .
ENV VITE_API_BASE_URL=""
RUN npm run build

# ========== Stage 4: Final Runtime Image ==========
FROM eclipse-temurin:25-jre
# Install Nginx
# Note: eclipse-temurin is usually Ubuntu-based.
RUN apt-get update && \
    apt-get install -y nginx && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Backend Jar
COPY --from=build-backend /app/target/*.jar app.jar

# Copy Frontend Static Files
# Creating standard directories for them
RUN mkdir -p /app/static/user /app/static/admin

COPY --from=build-user /app/dist /app/static/user
COPY --from=build-admin /app/dist /app/static/admin

# Copy Nginx Config
COPY nginx-unified.conf /etc/nginx/nginx.conf

# Copy Startup Script
COPY start_services.sh /app/start_services.sh
RUN chmod +x /app/start_services.sh

# Expose ports: 8080 (API), 80 (User Web), 81 (Admin Web)
EXPOSE 8080 80 81

ENTRYPOINT ["/app/start_services.sh"]
