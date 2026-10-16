FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl
# Le modèle est traité par envsubst au démarrage (variable HTTPS_PORT).
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY docker-entrypoint-certs.sh /docker-entrypoint.d/05-certs.sh
RUN chmod +x /docker-entrypoint.d/05-certs.sh
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
ENV HTTPS_PORT=443 NGINX_ENVSUBST_FILTER=HTTPS_PORT
EXPOSE 80 443
