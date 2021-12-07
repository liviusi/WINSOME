JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

ClientMain.java:
	$(JC) $(JFLAGS) src/$@ $(OUTPUTDIR)

ServerMain.java:
	$(JC) $(JFLAGS) src/$@ $(OUTPUTDIR)

build: ClientMain.java ServerMain.java

run-client: ClientMain.java
	java $(CP) ClientMain

run-server: ServerMain.java
	java $(CP) ServerMain

clean:
	rm -f ./bin/*.class