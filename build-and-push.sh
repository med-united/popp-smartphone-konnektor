#!/bin/bash
mvn install -Dquarkus.container-image.build=true
docker tag "popp-smartphone-konnektor:latest" "manuelb86/popp-smartphone-konnektor:2026-01-20"
docker push "manuelb86/popp-smartphone-konnektor:2026-01-20"
