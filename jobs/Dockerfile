# syntax=docker/dockerfile:1
#
# The jobs image: the `jobs` entry point (`jobs run <name>`), used by the Helm migrate hook and by every CronJob.
# Two steps, as for server: Gradle prepares the context, docker builds it. Gradle never runs inside `docker build`.
#   ./gradlew :jobs:installDist
#   docker build -f jobs/Dockerfile -t menura/jobs jobs/build/install/jobs

FROM eclipse-temurin:25.0.5_8-jre-noble

# The noble base already has a user with uid 1000, so the job runner gets 10001.
RUN groupadd --system --gid 10001 menura \
    && useradd --system --uid 10001 --gid menura --no-create-home menura

WORKDIR /app
COPY bin ./bin
COPY lib ./lib

# `jobs` is on PATH, so the chart's `command: [jobs, run, ping]` and `docker run <image> run ping` reach the same entry point.
ENV PATH="/app/bin:${PATH}"

USER 10001:10001
ENTRYPOINT ["jobs"]
