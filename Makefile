JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/:./libs/gson-2.8.6.jar" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

.conf-exc:
	$(JC) $(CP) $(JFLAGS) src/configuration/InvalidConfigException.java $(OUTPUTDIR)

.baseconf: .conf-exc
	$(JC) $(CP) $(JFLAGS) src/configuration/Configuration.java $(OUTPUTDIR)

.servconf: .baseconf
	$(JC) $(CP) $(JFLAGS) src/configuration/ServerConfiguration.java $(OUTPUTDIR)

.psw:
	$(JC) $(CP) $(JFLAGS) src/cryptography/Passwords.java $(OUTPUTDIR)

.tag-exc:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidTagException.java $(OUTPUTDIR)

.tag: .tag-exc
	$(JC) $(CP) $(JFLAGS) src/user/Tag.java $(OUTPUTDIR)

.login-exc:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidLoginException.java $(OUTPUTDIR)

.logout-exc:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidLogoutException.java $(OUTPUTDIR)

.taglist-exc:
	$(JC) $(CP) $(JFLAGS) src/user/TagListTooLongException.java $(OUTPUTDIR)

.cred-exc:
	$(JC) $(CP) $(JFLAGS) src/user/WrongCredentialsException.java $(OUTPUTDIR)

.user: .psw .tag .login-exc .logout-exc .taglist-exc .cred-exc
	$(JC) $(CP) $(JFLAGS) src/user/User.java $(OUTPUTDIR)

.psw-exc:
	$(JC) $(CP) $(JFLAGS) src/server/storage/PasswordNotValidException.java $(OUTPUTDIR)

.nameexists-exc:
	$(JC) $(CP) $(JFLAGS) src/server/storage/UsernameAlreadyExistsException.java $(OUTPUTDIR)

.illegalarchive-exc:
	$(JC) $(CP) $(JFLAGS) src/server/storage/IllegalArchiveException.java $(OUTPUTDIR)

.nosuchuser-exc:
	$(JC) $(CP) $(JFLAGS) src/server/storage/NoSuchUserException.java $(OUTPUTDIR)

.invname-exc:
	$(JC) $(CP) $(JFLAGS) src/server/storage/UsernameNotValidException.java $(OUTPUTDIR)

.userrmistorage: .psw-exc .nameexists-exc .invname-exc .user .nosuchuser-exc
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserRMIStorage.java $(OUTPUTDIR)

.userstorage: .userrmistorage .illegalarchive-exc
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserStorage.java $(OUTPUTDIR)

.usermap: .userstorage .userrmistorage
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserMap.java $(OUTPUTDIR)

.backup: .usermap
	$(JC) $(CP) $(JFLAGS) src/server/BackupTask.java $(OUTPUTDIR)

.rmi-task: .user .usermap
	$(JC) $(CP) $(JFLAGS) src/server/RMITask.java $(OUTPUTDIR)

.apiconstants:
	$(JC) $(CP) $(JFLAGS) src/api/Constants.java $(OUTPUTDIR)

.apicodes:
	$(JC) $(CP) $(JFLAGS) src/api/CommandCode.java $(OUTPUTDIR)

.rc:
	$(JC) $(CP) $(JFLAGS) src/api/ResponseCode.java $(OUTPUTDIR)

.response: .rc
	$(JC) $(CP) $(JFLAGS) src/api/Response.java $(OUTPUTDIR)

.communication:
	$(JC) $(CP) $(JFLAGS) src/api/Communication.java $(OUTPUTDIR)

.clientapi: .servconf .apiconstants .apicodes .communication .rc .response
	$(JC) $(CP) $(JFLAGS) src/api/Command.java $(OUTPUTDIR)

.client: .clientapi
	$(JC) $(CP) $(JFLAGS) src/ClientMain.java $(OUTPUTDIR)

.server: .servconf .psw .user .rmi-task .apiconstants .apicodes .backup .communication .rc
	$(JC) $(CP) $(JFLAGS) src/ServerMain.java $(OUTPUTDIR)

build: .server .client

run-client: .client
	java $(CP) ClientMain

run-server: .server
	java $(CP) ServerMain

clean:
	rm -rf ./bin/*
	@touch ./bin/.keep