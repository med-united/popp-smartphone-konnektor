#!/bin/bash
export JAVA_TOOL_OPTIONS="-Djavax.xml.accessExternalDTD=all -Djavax.xml.accessExternalSchema=all" && mvn install
