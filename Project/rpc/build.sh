#!/bin/bash

# package the maven project
mvn clean package
sleep 1
# move the jar package to destination folder
mkdir ./target/lib/

mv ./target/*.jar ./target/lib/