# ---- Build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Ajuste de memória para builds grandes do Angular no CI
RUN node --max-old-space-size=2048 ./node_modules/@angular/cli/bin/ng.js build --configuration=production

# ---- Runtime ----
FROM nginx:alpine
# Remove config default
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
# outputPath confirmado em angular.json: dist/ces-frontend/browser
COPY --from=build /app/dist/ces-frontend/browser /usr/share/nginx/html
COPY docker/env.template.js /opt/ces/env.template.js
COPY docker/40-env-config.sh /docker-entrypoint.d/40-env-config.sh
RUN chmod +x /docker-entrypoint.d/40-env-config.sh

EXPOSE 80 8080
