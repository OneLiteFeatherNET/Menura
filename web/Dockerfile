# syntax=docker/dockerfile:1
#
# The web image: the Vue app as static files on unprivileged nginx, port 8080.
# Build from the repository root, which is the build context (the root .dockerignore applies):
#   docker build -f web/Dockerfile -t menura/web .

# ---------------------------------------------------------------------------
# Builder: install with the lockfile and build web/dist. Only the built files
# reach the runtime image: no node_modules, no sources.
# ---------------------------------------------------------------------------
FROM node:26.12.1-trixie-slim AS builder

# The pnpm version of web/package.json `packageManager`, installed directly so the image
# does not depend on corepack (which current Node releases no longer bundle).
RUN npm install --global pnpm@10.33.4

WORKDIR /build/web

# The lockfile first, so the dependency layer survives source changes.
COPY web/package.json web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY web/ ./
RUN pnpm build

# ---------------------------------------------------------------------------
# Runtime: unprivileged nginx with the static files and the SPA fallback.
# ---------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.29.8-alpine AS runtime

COPY web/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/web/dist /usr/share/nginx/html

EXPOSE 8080

# uid 101 is the nginx user of the unprivileged image; it cannot bind ports below 1024.
USER 101
