# WINSOME: a reWardINg SOcial Media
Soluzione proposta per il progetto di Laboratorio di Reti.

## Struttura del progetto
```
.
├── bin <-- .class files
├── build <-- .jar files
├── configs <-- config files
│   ├── client.properties <-- default client config file
│   └── server.properties <-- default server config file
├── docs <-- documentation for this project
├── libs <-- libraries used
│   └── gson-2.8.6.jar
├── LICENSE
├── logs <-- .logs files
├── Makefile
├── README.md
├── report <-- report
│   ├── images
│   │   └── unipi-logo.png
│   ├── report.pdf <-- compiled report
│   └── report.tex <-- source code
├── run_client.sh <-- script to run client
├── run_server.sh <-- script to run server
├── src <-- source code goes in here
│   ├── api <-- API for the client
│   │   ├── CommandCode.java <-- handy constants used during client-server communication
│   │   ├── Communication.java <-- container for the methods send and receive
│   │   ├── ResponseCode.java <-- handy constants used during client-server communication
│   │   └── Response.java <-- package-private class used to parse server's responses
│   ├── client <-- package containing classes for the client
│   │   ├── Colors.java <-- handy constants used for pretty printing
│   │   ├── Command.java <-- class handling each and every command going over TCP
│   │   ├── MulticastInfo.java <-- used to store Multicast coordinates
│   │   ├── MulticastWorker.java <-- thread handling Multicast messages
│   │   ├── RMIFollowers.java <-- RMI interface
│   │   └── RMIFollowersSet.java <-- RMI class
│   ├── ClientMain.java <-- client main file
│   ├── configuration <-- package handling .properties files parsing
│   │   ├── Configuration.java <-- base class, handles client configuration files
│   │   ├── InvalidConfigException.java
│   │   └── ServerConfiguration.java <-- handles server configuration files
│   ├── cryptography <-- package handling cryptography
│   │   └── Passwords.java
│   ├── server <-- package for the classes used by the server
│   │   ├── BackupTask.java <-- thread handling storages backups
│   │   ├── LoggingTask.java <-- thread handling logging
│   │   ├── post <-- package defining posts
│   │   │   ├── InvalidCommentException.java
│   │   │   ├── InvalidGeneratorException.java
│   │   │   ├── InvalidPostException.java
│   │   │   ├── InvalidVoteException.java
│   │   │   ├── Post.java <-- abstract class defining methods to generate posts
│   │   │   └── RewinPost.java <-- actual post class
│   │   ├── RewardsTask.java <-- thread sending out multicast messages and updating storages with new rewards
│   │   ├── RMICallback.java <-- RMI interface
│   │   ├── RMICallbackService.java <-- RMI class
│   │   ├── RMITask.java <-- thread handling RMI objects
│   │   └── storage <-- package defining storing mechanisms used by the server
│   │   │   ├── IllegalArchiveException.java
│   │   │   ├── NoSuchPostException.java
│   │   │   ├── NoSuchUserException.java
│   │   │   ├── PasswordNotValidException.java
│   │   │   ├── PostMap.java <-- PostStorage backed up by (many) map(s)
│   │   │   ├── PostStorage.java <-- interface
│   │   │   ├── Storage.java <-- package private abstract class defining backup methods
│   │   │   ├── UserMap.java <-- UserStorage backed up by (many) map(s)
│   │   │   ├── UsernameAlreadyExistsException.java
│   │   │   ├── UsernameNotValidException.java
│   │   │   ├── UserRMIStorage.java <-- RMI interface
│   │   │   └── UserStorage.java <-- interface
│   │   └── user <-- package defining users
│   │       ├── InvalidAmountException.java
│   │       ├── InvalidLoginException.java
│   │       ├── InvalidLogoutException.java
│   │       ├── InvalidTagException.java
│   │       ├── SameUserException.java
│   │       ├── Tag.java
│   │       ├── TagListTooLongException.java
│   │       ├── Transaction.java
│   │       ├── User.java
│   │       └── WrongCredentialsException.java
│   └── ServerMain.java <-- server main class
└── storage <-- sample storage(s)
    ├── following.json
    ├── posts-interactions.json
    ├── posts.json
    ├── transactions.json
    └── users.json
```