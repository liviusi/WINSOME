JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/:./libs/gson-2.8.6.jar" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

TARGETS = run-client run-server build

.InvalidConfigException:
	$(JC) $(CP) $(JFLAGS) src/configuration/InvalidConfigException.java $(OUTPUTDIR)

.Configuration: .InvalidConfigException
	$(JC) $(CP) $(JFLAGS) src/configuration/Configuration.java $(OUTPUTDIR)

.ServerConfiguration: .Configuration
	$(JC) $(CP) $(JFLAGS) src/configuration/ServerConfiguration.java $(OUTPUTDIR)

.Passwords:
	$(JC) $(CP) $(JFLAGS) src/cryptography/Passwords.java $(OUTPUTDIR)

.InvalidTagException:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidTagException.java $(OUTPUTDIR)

.Tag: .InvalidTagException
	$(JC) $(CP) $(JFLAGS) src/user/Tag.java $(OUTPUTDIR)

.InvalidLoginException:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidLoginException.java $(OUTPUTDIR)

.InvalidLogoutException:
	$(JC) $(CP) $(JFLAGS) src/user/InvalidLogoutException.java $(OUTPUTDIR)

.TagListTooLongException:
	$(JC) $(CP) $(JFLAGS) src/user/TagListTooLongException.java $(OUTPUTDIR)

.WrongCredentialsException:
	$(JC) $(CP) $(JFLAGS) src/user/WrongCredentialsException.java $(OUTPUTDIR)

.SameUserException:
	$(JC) $(CP) $(JFLAGS) src/user/SameUserException.java $(OUTPUTDIR)

.User: .Passwords .Tag .InvalidLoginException .InvalidLogoutException .TagListTooLongException .WrongCredentialsException .SameUserException
	$(JC) $(CP) $(JFLAGS) src/user/User.java $(OUTPUTDIR)

.InvalidCommentException:
	$(JC) $(CP) $(JFLAGS) src/server/post/InvalidCommentException.java $(OUTPUTDIR)

.InvalidGeneratorException:
	$(JC) $(CP) $(JFLAGS) src/server/post/InvalidGeneratorException.java $(OUTPUTDIR)

.InvalidPostException:
	$(JC) $(CP) $(JFLAGS) src/server/post/InvalidPostException.java $(OUTPUTDIR)

.InvalidVoteException:
	$(JC) $(CP) $(JFLAGS) src/server/post/InvalidVoteException.java $(OUTPUTDIR)

.Post: .InvalidCommentException .InvalidGeneratorException .InvalidPostException .InvalidVoteException
	$(JC) $(CP) $(JFLAGS) src/server/post/Post.java $(OUTPUTDIR)

.RewinPost: .Post
	$(JC) $(CP) $(JFLAGS) src/server/post/RewinPost.java $(OUTPUTDIR)

.PasswordNotValidException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/PasswordNotValidException.java $(OUTPUTDIR)

.UsernameAlreadyExistsException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/UsernameAlreadyExistsException.java $(OUTPUTDIR)

.IllegalArchiveException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/IllegalArchiveException.java $(OUTPUTDIR)

.NoSuchUserException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/NoSuchUserException.java $(OUTPUTDIR)

.UsernameNotValidException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/UsernameNotValidException.java $(OUTPUTDIR)

.UserRMIStorage: .PasswordNotValidException .UsernameAlreadyExistsException .UsernameNotValidException .User .NoSuchUserException
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserRMIStorage.java $(OUTPUTDIR)

.Storage:
	$(JC) $(CP) $(JFLAGS) src/server/storage/Storage.java $(OUTPUTDIR)

.UserStorage: .UserRMIStorage .IllegalArchiveException .Storage
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserStorage.java $(OUTPUTDIR)

.UserMap: .UserStorage .UserRMIStorage
	$(JC) $(CP) $(JFLAGS) src/server/storage/UserMap.java $(OUTPUTDIR)

.NoSuchPostException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/NoSuchPostException.java $(OUTPUTDIR)

.PostStorage: .RewinPost .NoSuchPostException
	$(JC) $(CP) $(JFLAGS) src/server/storage/PostStorage.java $(OUTPUTDIR)

.PostMap: .PostStorage
	$(JC) $(CP) $(JFLAGS) src/server/storage/PostMap.java $(OUTPUTDIR)

.BackupTask: .UserMap .ServerConfiguration
	$(JC) $(CP) $(JFLAGS) src/server/BackupTask.java $(OUTPUTDIR)

.RMIFollowers:
	$(JC) $(CP) $(JFLAGS) src/client/RMIFollowers.java $(OUTPUTDIR)

.RMIFollowersSet: .RMIFollowers
	$(JC) $(CP) $(JFLAGS) src/client/RMIFollowersSet.java $(OUTPUTDIR)

.RMICallback: .RMIFollowers
	$(JC) $(CP) $(JFLAGS) src/server/RMICallback.java $(OUTPUTDIR)

.RMICallbackService: .RMICallback
	$(JC) $(CP) $(JFLAGS) src/server/RMICallbackService.java $(OUTPUTDIR)

.RMITask: .User .UserMap .RMICallbackService .PostMap
	$(JC) $(CP) $(JFLAGS) src/server/RMITask.java $(OUTPUTDIR)

.CommandCode:
	$(JC) $(CP) $(JFLAGS) src/api/CommandCode.java $(OUTPUTDIR)

.ResponseCode:
	$(JC) $(CP) $(JFLAGS) src/api/ResponseCode.java $(OUTPUTDIR)

.Response: .ResponseCode
	$(JC) $(CP) $(JFLAGS) src/api/Response.java $(OUTPUTDIR)

.Communication:
	$(JC) $(CP) $(JFLAGS) src/api/Communication.java $(OUTPUTDIR)

.Command: .ServerConfiguration .CommandCode .Communication .ResponseCode .Response
	$(JC) $(CP) $(JFLAGS) src/api/Command.java $(OUTPUTDIR)

ClientMain: .Command .RMIFollowersSet .RMICallback
	$(JC) $(CP) $(JFLAGS) src/ClientMain.java $(OUTPUTDIR)

ServerMain: .ServerConfiguration .Passwords .User .RMITask .CommandCode .BackupTask .Communication .ResponseCode
	$(JC) $(CP) $(JFLAGS) src/ServerMain.java $(OUTPUTDIR)

build: ServerMain ClientMain

run-client: ClientMain
	java $(CP) ClientMain

run-server: ServerMain
	java $(CP) ServerMain

clean:
	rm -rf ./bin/*
	@touch ./bin/.keep