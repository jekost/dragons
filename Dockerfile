# One image: the Spring Boot backend serves the built Vue SPA as static files, so the browser, the
# SPA and /api share one origin (no proxy, no CORS). The SPA is copied into the backend's static
# resources only inside this build; the repo's own Maven and npm builds are unchanged.
#
#   docker build -t dragons .
#   docker run --rm -p 3001:3001 dragons      # then open http://localhost:3001

# --- 1. Frontend: type-check and bundle the SPA -------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /repo
# Manifests first, so the dependency layer is cached until they change.
COPY package.json package-lock.json tsconfig.base.json ./
COPY packages/frontend/package.json packages/frontend/
RUN npm ci --workspace @dom/frontend --include-workspace-root
COPY packages/frontend packages/frontend
RUN npm run build --workspace @dom/frontend

# --- 2. Backend: build the runnable jar with the SPA baked in ----------------------------------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /repo
# The aggregator lists both modules, so the reactor needs the characterization pom to exist even
# though only the backend is built (-pl :dragons-backend -am).
COPY pom.xml ./
COPY packages/backend/pom.xml packages/backend/
COPY packages/characterization/pom.xml packages/characterization/
RUN mvn -q -B -pl :dragons-backend -am dependency:go-offline
COPY packages/backend/src packages/backend/src
COPY --from=frontend /repo/packages/frontend/dist packages/backend/src/main/resources/static
# Tests run in `npm run test:all`, not on every image build.
RUN mvn -q -B -pl :dragons-backend -am -DskipTests package

# --- 3. Runtime: JRE only ------------------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home app
# The -exec classifier is the repackaged, runnable jar (the plain one is a library for the tool).
COPY --from=backend /repo/packages/backend/target/dragons-backend-1.0.0-exec.jar app.jar
USER app
ENV PORT=3001
EXPOSE 3001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
