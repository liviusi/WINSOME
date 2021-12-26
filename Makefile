JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/:./libs/gson-2.8.6.jar" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

ServerConfiguration.java:
	$(JC) $(CP) $(JFLAGS) src/configuration/Constants.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/configuration/InvalidConfigException.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/configuration/$@ $(OUTPUTDIR)

Passwords.java:
	$(JC) $(CP) $(JFLAGS) src/cryptography/$@ $(OUTPUTDIR)

ClientMain.java:
	$(JC) $(CP) $(JFLAGS) src/$@ $(OUTPUTDIR)

User.java: Passwords.java
	$(JC) $(CP) $(JFLAGS) src/user/InvalidTagException.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/user/Tag.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/user/User.java $(OUTPUTDIR)

ServerMain.java: ServerConfiguration.java Passwords.java User.java
	$(JC) $(CP) $(JFLAGS) src/$@ $(OUTPUTDIR)

rmi-server: User.java
	$(JC) $(CP) $(JFLAGS) src/server/rmi/PasswordNotValidException.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UsernameAlreadyExistsException.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UsernameNotValidException.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UserStorage.java $(OUTPUTDIR)
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UserSet.java $(OUTPUTDIR)

build: ClientMain.java ServerMain.java

run-client: ClientMain.java
	java $(CP) ClientMain

run-server: ServerMain.java
	java $(CP) ServerMain ./configs/config.txt

clean:
	rm -rf ./bin/*
	@touch ./bin/.keep