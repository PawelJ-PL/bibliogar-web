FROM postgres:12.0

RUN sed -i -e "s/listen_addresses=''/listen_addresses='*'/g" /usr/local/bin/docker-entrypoint.sh

RUN apt-get update && apt-get install --no-install-recommends -y wget openjdk-11-jre-headless unzip
RUN wget -P /tmp/ https://github.com/liquibase/liquibase/releases/download/liquibase-parent-3.7.0/liquibase-3.7.0-bin.zip
RUN unzip -d /tmp/liquibase /tmp/liquibase-3.7.0-bin.zip && chmod +x /tmp/liquibase/liquibase
RUN wget -P /tmp/liquibase/lib/ https://repo1.maven.org/maven2/org/postgresql/postgresql/42.2.8/postgresql-42.2.8.jar

COPY resources/changelog-master.yml /tmp
COPY init/ /docker-entrypoint-initdb.d/