JC = javac # compiler
JFLAGS = -g
CP = -cp ".:./bin/:./libs/gson-2.8.6.jar" # classpath
JV = java
OUTPUTDIR = -d ./bin

.DEFAULT_GOAL := build

.PHONY: clean

TARGETS = client server build all

.InvalidConfigException:
	$(JC) $(CP) $(JFLAGS) src/configuration/InvalidConfigException.java $(OUTPUTDIR)

.Configuration: .InvalidConfigException
	$(JC) $(CP) $(JFLAGS) src/configuration/Configuration.java $(OUTPUTDIR)

.ServerConfiguration: .Configuration
	$(JC) $(CP) $(JFLAGS) src/configuration/ServerConfiguration.java $(OUTPUTDIR)

.Passwords:
	$(JC) $(CP) $(JFLAGS) src/cryptography/Passwords.java $(OUTPUTDIR)

.InvalidTagException:
	$(JC) $(CP) $(JFLAGS) src/api/rmi/InvalidTagException.java $(OUTPUTDIR)

.Tag: .InvalidTagException
	$(JC) $(CP) $(JFLAGS) src/server/user/Tag.java $(OUTPUTDIR)

.InvalidLoginException:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidLoginException.java $(OUTPUTDIR)

.InvalidLogoutException:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidLogoutException.java $(OUTPUTDIR)

.TagListTooLongException:
	$(JC) $(CP) $(JFLAGS) src/api/rmi/TagListTooLongException.java $(OUTPUTDIR)

.WrongCredentialsException:
	$(JC) $(CP) $(JFLAGS) src/server/user/WrongCredentialsException.java $(OUTPUTDIR)

.SameUserException:
	$(JC) $(CP) $(JFLAGS) src/server/user/SameUserException.java $(OUTPUTDIR)

.InvalidAmountException:
	$(JC) $(CP) $(JFLAGS) src/server/user/InvalidAmountException.java $(OUTPUTDIR)

.Transaction: .InvalidAmountException
	$(JC) $(CP) $(JFLAGS) src/server/user/Transaction.java $(OUTPUTDIR)

.User: .Passwords .Tag .InvalidLoginException .InvalidLogoutException .TagListTooLongException .WrongCredentialsException .SameUserException .Transaction
	$(JC) $(CP) $(JFLAGS) src/server/user/User.java $(OUTPUTDIR)

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
	$(JC) $(CP) $(JFLAGS) src/api/rmi/PasswordNotValidException.java $(OUTPUTDIR)

.UsernameAlreadyExistsException:
	$(JC) $(CP) $(JFLAGS) src/api/rmi/UsernameAlreadyExistsException.java $(OUTPUTDIR)

.IllegalArchiveException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/IllegalArchiveException.java $(OUTPUTDIR)

.NoSuchUserException:
	$(JC) $(CP) $(JFLAGS) src/server/storage/NoSuchUserException.java $(OUTPUTDIR)

.UsernameNotValidException:
	$(JC) $(CP) $(JFLAGS) src/api/rmi/UsernameNotValidException.java $(OUTPUTDIR)

.UserRMIStorage: .PasswordNotValidException .UsernameAlreadyExistsException .UsernameNotValidException .User .NoSuchUserException
	$(JC) $(CP) $(JFLAGS) src/api/rmi/UserRMIStorage.java $(OUTPUTDIR)

.Storage:
	$(JC) $(CP) $(JFLAGS) src/server/storage/Storage.java $(OUTPUTDIR)

.UserStorage: .UserRMIStorage .IllegalArchiveException .Storage .Post
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

.RewardsTask: .PostStorage .UserStorage
	$(JC) $(CP) $(JFLAGS) src/server/RewardsTask.java $(OUTPUTDIR)

.LoggingTask: .ServerConfiguration
	$(JC) $(CP) $(JFLAGS) src/server/LoggingTask.java $(OUTPUTDIR)

.RMIFollowers:
	$(JC) $(CP) $(JFLAGS) src/api/rmi/RMIFollowers.java $(OUTPUTDIR)

.MulticastInfo:
	$(JC) $(CP) $(JFLAGS) src/client/MulticastInfo.java $(OUTPUTDIR)

.MulticastWorker:
	$(JC) $(CP) $(JFLAGS) src/client/MulticastWorker.java $(OUTPUTDIR)

.RMIFollowersSet: .RMIFollowers
	$(JC) $(CP) $(JFLAGS) src/client/RMIFollowersSet.java $(OUTPUTDIR)

.RMICallback: .RMIFollowers
	$(JC) $(CP) $(JFLAGS) src/api/rmi/RMICallback.java $(OUTPUTDIR)

.RMICallbackService: .RMICallback
	$(JC) $(CP) $(JFLAGS) src/server/RMICallbackService.java $(OUTPUTDIR)

.RMITask: .User .UserMap .RMICallbackService .PostMap
	$(JC) $(CP) $(JFLAGS) src/server/RMITask.java $(OUTPUTDIR)

.CommandCode:
	$(JC) $(CP) $(JFLAGS) src/api/CommandCode.java $(OUTPUTDIR)

.ResponseCode:
	$(JC) $(CP) $(JFLAGS) src/api/ResponseCode.java $(OUTPUTDIR)

.Response: .ResponseCode
	$(JC) $(CP) $(JFLAGS) src/client/Response.java $(OUTPUTDIR)

.Communication:
	$(JC) $(CP) $(JFLAGS) src/api/Communication.java $(OUTPUTDIR)

.Colors:
	$(JC) $(CP) $(JFLAGS) src/client/Colors.java $(OUTPUTDIR)

.Command: .CommandCode .Communication .ResponseCode .Response .Colors
	$(JC) $(CP) $(JFLAGS) src/client/Command.java $(OUTPUTDIR)

client: .Command .RMIFollowersSet .RMICallback .MulticastWorker .MulticastInfo
	$(JC) $(CP) $(JFLAGS) src/ClientMain.java $(OUTPUTDIR)

server: .ServerConfiguration .Passwords .User .RMITask .CommandCode .BackupTask .Communication .ResponseCode .RewardsTask .LoggingTask
	$(JC) $(CP) $(JFLAGS) src/ServerMain.java $(OUTPUTDIR)

all: clean build

build: server client
	cd bin && jar -cevf ServerMain ../build/ServerMain.jar -C . api client configuration cryptography server server/post server/storage server/user && cd ..
	cd bin && jar -cevf ClientMain ../build/ClientMain.jar -C . cryptography/ configuration/ client/ api/ server/user/ && cd ..

clean:
	rm -rf ./bin/* ./build/*.jar
	@touch ./bin/.keep