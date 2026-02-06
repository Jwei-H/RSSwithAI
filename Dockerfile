# ========== Stage 1: Build Backend (Java) ==========
FROM maven:3.9-eclipse-temurin-25 AS build-backend
WORKDIR /app
COPY settings.xml /usr/share/maven/conf/settings.xml
COPY pom.xml .
COPY src ./src
# Skip tests to speed up build, adding retry to help with network flakes
RUN mvn clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=3

# ========== Stage 2: Build User Frontend (Node) ==========
FROM node:22-alpine AS build-user
WORKDIR /app
# Optimize Node memory for 2GB server (leave ~500MB for OS/Daemon)
ENV NODE_OPTIONS="--max-old-space-size=1536"
COPY fronted-user/package.json ./
# Use Aliyun mirror for faster install in China
RUN npm config set registry https://registry.npmmirror.com/
RUN npm install
COPY fronted-user/ .
# Ensure VITE_API_BASE_URL is relative for web hosting, OR empty to default to code logic
ENV VITE_API_BASE_URL=""
RUN npm run build

# ========== Stage 3: Build Admin Frontend (Node) ==========
FROM node:22-alpine AS build-admin
WORKDIR /app
# Optimize Node memory for 2GB server (leave ~500MB for OS/Daemon)
ENV NODE_OPTIONS="--max-old-space-size=1536"
COPY fronted-admin/package.json ./
# Use Aliyun mirror for faster install in China
RUN npm config set registry https://registry.npmmirror.com/
RUN npm install
COPY fronted-admin/ .
ENV VITE_API_BASE_URL=""
RUN npm run build

# ========== Stage 4: Final Runtime Image ==========
FROM eclipse-temurin:25-jre
# Switch to Aliyun mirrors for speed/reliability in CN
RUN if [ -f /etc/apt/sources.list ]; then sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list && sed -i 's/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list; fi
RUN if [ -f /etc/apt/sources.list.d/debian.sources ]; then sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list.d/debian.sources; fi
# Handle Ubuntu based images if applicable
RUN if [ -f /etc/apt/sources.list ]; then sed -i 's/archive.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list && sed -i 's/security.ubuntu.com/mirrors.aliyun.com/g' /etc/apt/sources.list; fi

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

# Expose ports: 8080 (API), 5777 (User Web), 5173 (Admin Web)
EXPOSE 8080 5777 5173

ENTRYPOINT ["/app/start_services.sh"]
