JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/:./libs/gson-2.8.6.jar" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

.confconstants:
	$(JC) $(CP) $(JFLAGS) src/configuration/Constants.java $(OUTPUTDIR)

.conf-exc:
	$(JC) $(CP) $(JFLAGS) src/configuration/InvalidConfigException.java $(OUTPUTDIR)

.baseconf: .confconstants .conf-exc
	$(JC) $(CP) $(JFLAGS) src/configuration/Configuration.java $(OUTPUTDIR)

.servconf: .baseconf
	$(JC) $(CP) $(JFLAGS) src/configuration/ServerConfiguration.java $(OUTPUTDIR)

.psw:
	$(JC) $(CP) $(JFLAGS) src/cryptography/Passwords.java $(OUTPUTDIR)

.tag-exc:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidTagException.java $(OUTPUTDIR)

.tag: .tag-exc
	$(JC) $(CP) $(JFLAGS) src/server/user/Tag.java $(OUTPUTDIR)

.login-exc:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidLoginException.java $(OUTPUTDIR)

.logout-exc:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidLogoutException.java $(OUTPUTDIR)

.taglist-exc:
	$(JC) $(CP) $(JFLAGS) src/server/user/TagListTooLongException.java $(OUTPUTDIR)

.cred-exc:
	$(JC) $(CP) $(JFLAGS) src/server/user/WrongCredentialsException.java $(OUTPUTDIR)

.user: .psw .tag .login-exc .logout-exc .taglist-exc .cred-exc
	$(JC) $(CP) $(JFLAGS) src/server/user/User.java $(OUTPUTDIR)

.psw-exc:
	$(JC) $(CP) $(JFLAGS) src/server/rmi/PasswordNotValidException.java $(OUTPUTDIR)

.nameexists-exc:
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UsernameAlreadyExistsException.java $(OUTPUTDIR)

.invname-exc:
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UsernameNotValidException.java $(OUTPUTDIR)

.userstorage: .psw-exc .nameexists-exc .invname-exc
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UserStorage.java $(OUTPUTDIR)

.userset: .userstorage
	$(JC) $(CP) $(JFLAGS) src/server/rmi/UserSet.java $(OUTPUTDIR)

.rmi-server: .user .userset
	$(JC) $(CP) $(JFLAGS) src/server/rmi/PasswordNotValidException.java $(OUTPUTDIR)

.rmi-task: .rmi-server
	$(JC) $(CP) $(JFLAGS) src/server/RMITask.java $(OUTPUTDIR)

.apiconstants:
	$(JC) $(CP) $(JFLAGS) src/api/Constants.java $(OUTPUTDIR)

.apicodes:
	$(JC) $(CP) $(JFLAGS) src/api/CommandCode.java $(OUTPUTDIR)

.clientapi: .rmi-server .servconf .apiconstants .apicodes
	$(JC) $(CP) $(JFLAGS) src/api/Command.java $(OUTPUTDIR)

.serverapi: .user .userset
	$(JC) $(CP) $(JFLAGS) src/server/API.java $(OUTPUTDIR)

.client: .clientapi
	$(JC) $(CP) $(JFLAGS) src/ClientMain.java $(OUTPUTDIR)

.server: .servconf .psw .user .rmi-task .apiconstants .apicodes .serverapi
	$(JC) $(CP) $(JFLAGS) src/ServerMain.java $(OUTPUTDIR)

build: .server .client

run-client: .client
	java $(CP) ClientMain

run-server: .server
	java $(CP) ServerMain

clean:
	rm -rf ./bin/*
	@touch ./bin/.keep