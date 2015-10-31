#!/usr/bin/env bash


# package the maven project
mvn clean compile assembly:single
sleep 2
# move the jar package to destination folder
mkdir ./target/lib/

mv ./target/*.jar ./target/lib/