# ENUVEX - Spring Boot backend serving both the API and the static frontend.
#
# The build context must be the repository ROOT, not backend/: pom.xml copies
# the HTML/CSS/JS from the directories sitting next to backend/ into the jar.
#   docker build -t enuvex .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# POM first so dependency downloads are cached independently of source edits.
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml dependency:go-offline

# Mirror the on-disk layout the POM expects: ../assets, .., ../job-seeker, ...
COPY backend/src backend/src
COPY assets assets
COPY index.html layout.html register.html ./
COPY job-seeker job-seeker
COPY employer employer
COPY admin admin

RUN mvn -B -f backend/pom.xml -DskipTests package


FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user. Uploads are written to ./uploads, so that directory
# has to exist and be writable by the same user. On hosts with an ephemeral
# filesystem uploaded avatars are lost on redeploy - mount a volume at
# /app/uploads if you need them to survive (see DEPLOYMENT.md).
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/uploads && chown -R app:app /app

# Only the executable jar is copied; *.jar.original does not match this glob.
COPY --from=build --chown=app:app /src/backend/target/*.jar app.jar

USER app

# PORT is overridden by the hosting platform; application.yml falls back to 8080.
# MaxRAMPercentage keeps the JVM inside a small container's memory limit.
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
