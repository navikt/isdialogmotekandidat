FROM navikt/java:17
COPY build/libs/app.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
