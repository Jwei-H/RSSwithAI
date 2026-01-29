#!/bin/sh
# Start Nginx in background
nginx

# Start Spring Boot Application
# Using exec so java process takes over PID 1 (optional, but good for signal handling if shell was PID 1)
# However, since we have nginx running in background, we keep the shell or just verify.
# A simple way to run both is:
exec java -jar app.jar
