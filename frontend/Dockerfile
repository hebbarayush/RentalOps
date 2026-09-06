# syntax=docker/dockerfile:1

# ---------- build ----------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Absolute API URL for a split deploy (frontend and backend on different hosts).
# Leave empty for the docker-compose stack, where nginx proxies /api to the backend.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

# nginx:alpine renders /etc/nginx/templates/*.template through envsubst on startup.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Where nginx forwards /api. Overridden per environment; the compose default is the backend service.
ENV BACKEND_ORIGIN=http://backend:8080

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
    CMD wget -q -O- http://127.0.0.1/healthz | grep -q ok || exit 1
