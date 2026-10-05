# syntax=docker/dockerfile:1
#
# Frontend image: build the React app with Node, serve the static files with nginx, which also
# proxies /api to the backend so the browser talks to ONE origin (no CORS).
# Build from the REPOSITORY ROOT (the tests type-checked by `npm run build` import
# ../contracts/api-samples):
#   docker build -f frontend/Dockerfile -t currency-frontend .
# The output is plain HTML/JS/CSS, so the build stage runs natively on $BUILDPLATFORM.

FROM --platform=$BUILDPLATFORM node:26.10.0-alpine AS build
WORKDIR /repo/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY contracts /repo/contracts
COPY frontend ./
RUN npm run build

# Unprivileged nginx (runs as uid 101, listens on 8080) - fits Kubernetes' non-root policies.
FROM nginxinc/nginx-unprivileged:1.31.6-alpine
# Rendered at startup by the image's entrypoint (envsubst on /etc/nginx/templates/*.template).
ENV BACKEND_URL=http://backend:8080
COPY frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /repo/frontend/dist /usr/share/nginx/html
EXPOSE 8080
