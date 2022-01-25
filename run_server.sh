#!/usr/bin/env bash

if ! [ -e ./bin/ServerMain.class ]; then
	make server
fi
java -cp ".:./bin/:./libs/gson-2.8.6.jar" ServerMain