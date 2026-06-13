# syntax=docker/dockerfile:1

# --- Build stage: build the React app ---
# Context for this image is the REPO ROOT, because @rota/web depends on the
# @rota/api-client workspace package. The root .dockerignore keeps the context lean.
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo

# pnpm@11.3.0 is pinned via the root package.json "packageManager" field.
COPY . .
# Install only @rota/web and its workspace dependencies (api-client), not the whole repo.
RUN pnpm install --frozen-lockfile --filter @rota/web...
RUN pnpm --filter @rota/web build

# --- Runtime stage: serve the static build through nginx ---
FROM nginx:alpine AS runtime
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
EXPOSE 80
