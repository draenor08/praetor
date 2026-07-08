# Multi-stage: build the Angular app with Node, serve static files via nginx.
FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build -- --configuration docker

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/praetor/browser /usr/share/nginx/html
EXPOSE 80
