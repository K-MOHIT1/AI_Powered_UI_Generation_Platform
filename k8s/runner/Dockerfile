FROM node:20-alpine AS dependencies
WORKDIR /opt/starter
COPY package.json ./
RUN npm install --no-audit --no-fund

FROM node:20-alpine
WORKDIR /app
COPY --from=dependencies /root/.npm /root/.npm
COPY --from=dependencies /opt/starter/node_modules /opt/starter/node_modules
COPY --from=dependencies /opt/starter/package.json /opt/starter/package.json

ENV npm_config_cache=/root/.npm
CMD ["/bin/sh", "-c", "sleep infinity"]