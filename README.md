# WINSOME: a reWardINg SOcial Media
Soluzione proposta per il progetto di Laboratorio di Reti.

## Struttura del progetto
```
.
├── bin
├── build
├── configs
│   ├── client.txt
│   └── server.txt
├── libs
│   └── gson-2.8.6.jar
├── LICENSE
├── Makefile
├── README.md
├── Soluzione dellesercizio-20211214
│   ├── Client.class
│   ├── Client.java
│   ├── Server.class
│   ├── Server.java
│   ├── UserList.class
│   ├── UserListInterface.class
│   ├── UserListInterface.java
│   └── UserList.java
├── src
│   ├── api
│   │   ├── CommandCode.java
│   │   ├── Command.java
│   │   ├── Communication.java
│   │   └── Constants.java
│   ├── ClientMain.java
│   ├── configuration
│   │   ├── Configuration.java
│   │   ├── Constants.java
│   │   ├── InvalidConfigException.java
│   │   └── ServerConfiguration.java
│   ├── cryptography
│   │   └── Passwords.java
│   ├── server
│   │   ├── API.java
│   │   ├── BackupTask.java
│   │   ├── rmi
│   │   │   ├── PasswordNotValidException.java
│   │   │   ├── UserMap.java
│   │   │   ├── UsernameAlreadyExistsException.java
│   │   │   ├── UsernameNotValidException.java
│   │   │   └── UserStorage.java
│   │   ├── RMITask.java
│   │   └── user
│   │       ├── InvalidLoginException.java
│   │       ├── InvalidLogoutException.java
│   │       ├── InvalidTagException.java
│   │       ├── Tag.java
│   │       ├── TagListTooLongException.java
│   │       ├── User.java
│   │       └── WrongCredentialsException.java
│   └── ServerMain.java
└── storage
    └── users.json
```