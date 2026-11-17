FROM node:24.22.0-alpine3.24 AS build
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31.8-alpine3.24
RUN apk upgrade --no-cache
RUN mkdir -p /tmp/nginx /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp \
    /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp /var/cache/nginx/scgi_temp \
    && chown -R 101:101 /tmp/nginx /var/cache/nginx
COPY nginx-unprivileged.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN nginx -t
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
