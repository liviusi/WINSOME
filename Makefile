JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

InvalidConfigException.java:
	$(JC) $(CP) $(JFLAGS) src/configuration/$@ $(OUTPUTDIR)

Configuration.java: InvalidConfigException.java
	$(JC) $(CP) $(JFLAGS) src/configuration/$@ $(OUTPUTDIR)

ClientMain.java:
	$(JC) $(CP) $(JFLAGS) src/$@ $(OUTPUTDIR)

ServerMain.java: Configuration.java
	$(JC) $(CP) $(JFLAGS) src/$@ $(OUTPUTDIR)

build: ClientMain.java ServerMain.java

run-client: ClientMain.java
	java $(CP) ClientMain

run-server: ServerMain.java
	java $(CP) ServerMain ./configs/config.txt

clean:
	rm -rf ./bin/configuration ./bin/*.class