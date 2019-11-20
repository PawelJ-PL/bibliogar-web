#!/bin/bash

/tmp/liquibase/liquibase --url="jdbc:postgresql://127.0.0.1:5432/bibliogar" --changeLogFile="/tmp/changelog-master.yml" --username="bibliogar" --password="secret" update