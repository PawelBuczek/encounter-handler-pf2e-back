# encounterhandlerpf2e
Encounter Handler for TTRPG system Pathfinder 2e (remaster)

This is done as a training exercise.  
System logic will be based on: https://2e.aonprd.com/  
Using MariaDB database.  


Recommended to install first:
- Java (21)
- git (with Git Bash)
- some docker tool (for example Docker Desktop)

# How to set up locally:
1. Download https://github.com/PawelBuczek/encounter-handler-pf2e-back (this repo)
2. For now the only external dependency is MariaDB database.  
   You may install it locally if you want to, matching it with src/main/resources/application.properties file.  
   But it's easier with docker:  
   - install (and run) rancher desktop or docker desktop or any other docker tool that you like
   - download mariadb image with command `docker pull mariadb`  
     if you want, you can read more about this image on https://hub.docker.com/_/mariadb
   - now we need to run this image in a container. For example running db in `docker-compose.yaml` file.
     (you can also run app there if you prefer, then probably ignore step 3)
3. Run `src/main/java/com/pbuczek/pf/Application.java` *(database structure will be created by Liquibase)*

# DBeaver (or other db management tool) connection properties to connect with MariaDB server:
*(if you changed setup of db, please adjust below values accordingly)*  
`Server Host: 127.0.0.1`  `Port: 3306`  `Username: root`  `Password: PASSWORD`  `Driver name: MariaDB`  
leave `Database` blank  
_note: for some tools it may be required to set driver property `allowPublicKeyRetrieval` to `true`_

# Containerization with Docker:
1. Run Maven goal: `mvn clean package -DskipTests`
2. Build Docker images locally: using `docker-compose.yaml`

# Running tests:
- Tests are divided into UnitTests, ApplicationTests and IntegrationTests
- UnitTests can be run on their own
- in order to run ApplicationTests or IntegrationTests, MariaDB database needs to be accessible
  (for example by running db in `docker-compose.yaml`)

# Notes about usage:
- there are 2 User types, STANDARD and ADMIN
- when creating new User, it will always have STANDARD type
- only ADMIN type users are allowed to change user types, 
  so if you want to test out ADMIN only endpoints you need to change user type in database
- authentication & authorization can be done by Basic Auth (providing username and password)
  or by API Key (using X-API-KEY header). API Keys are always bound to a User and have the same authorities
  or by using Oauth 2.0 JWT Token (as usual with Refresh Tokens and Access Tokens)  
  (by default authorization server runs on port 9000)