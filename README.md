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
├── src
│   ├── api
│   │   ├── CommandCode.java
│   │   ├── Command.java
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
│   │   ├── rmi
│   │   │   ├── PasswordNotValidException.java
│   │   │   ├── UsernameAlreadyExistsException.java
│   │   │   ├── UsernameNotValidException.java
│   │   │   ├── UserSet.java
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
```