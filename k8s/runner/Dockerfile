FROM node:20-alpine AS dependencies

WORKDIR /opt/starter
COPY package.json ./

# Keep npm's download cache in the final runner image. Do not preload the
# node_modules tree: every generated project has its own dependency graph.
RUN npm install --no-audit --no-fund

FROM node:20-alpine

WORKDIR /app
COPY --from=dependencies /root/.npm /root/.npm

ENV npm_config_cache=/root/.npm
CMD ["/bin/sh", "-c", "sleep infinity"]
