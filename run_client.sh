#!/usr/bin/env bash

if ! [ -e ./bin/ClientMain.class ]; then
	make client
fi
java -cp ".:./bin/:./libs/gson-2.8.6.jar" ClientMain