# syntax=docker/dockerfile:1
#
# Gateway image: builds the React frontend, then serves it with Nginx.
# Build context is the repository root:
#   docker build -f gateway/Dockerfile -t homeflix-gateway .

# ---- Build stage: compile the React app ----
FROM node:22 AS build
WORKDIR /app

# Install dependencies first so this layer is cached until the lockfile changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---- Runtime stage: unprivileged Nginx ----
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY gateway/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

# Unprivileged Nginx listens on 8080.
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
