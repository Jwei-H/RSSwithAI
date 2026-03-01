#!/bin/sh

JWT_SECRET_FILE="/app/data/jwt_secret"

mkdir -p /app/data

if [ -z "${JWT_SECRET:-}" ]; then
	if [ -f "$JWT_SECRET_FILE" ]; then
		JWT_SECRET=$(cat "$JWT_SECRET_FILE")
	else
		JWT_SECRET=$(head -c 48 /dev/urandom | base64 | tr -d '\n')
		printf "%s" "$JWT_SECRET" > "$JWT_SECRET_FILE"
		chmod 600 "$JWT_SECRET_FILE" || true
	fi
	export JWT_SECRET
fi

# Start Nginx in background
nginx

# Start Spring Boot Application
# Using exec so java process takes over PID 1 (optional, but good for signal handling if shell was PID 1)
# However, since we have nginx running in background, we keep the shell or just verify.
# A simple way to run both is:
exec java -jar app.jar
