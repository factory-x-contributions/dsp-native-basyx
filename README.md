# BaSyx-Starter-App

### What is does

This is pretty much a standard Spring Boot Project, that has dependencies on the dsp-protocol-library as well as some 
components of the [Basyx framework](https://mvnrepository.com/artifact/org.eclipse.digitaltwin.basyx).

### Build it

You need to have the dsp-protocol-library in your Maven Local Repository. 

Use the Maven wrapper to create a .jar file in the target folder: 

```
./mvnw clean package -DskipTests
```

### Dockerize it

The Dockerfile assumes that you have already built a .jar file in the previous step. 

```
docker build -t basyxstarterapp .
```

### Deploy it via docker compose

Run a small testing environment that includes a very basic EDC Controlplane as a customer partner. 
We are using the EDC Controlplane image, that is also used in the embedded-connector-testbed repository, 
so you will need to fetch it from there. 

#### 1. Create a PAT on your GitHub account with "read:packages" privilege. 

#### 2. Login with that PAT
```
docker login ghcr.io -u <your-github-username> -p <your-personal-access-token>
```

#### 3. Pull it
```
docker pull ghcr.io/factory-x-contributions/basicedc:0.13.0
```

#### 4. Start it

```
docker compose up
```

Or use the shell script 

```
sh rundocker.sh
```

That script builds a .jar file from this project, creates a docker image from it and then launches the docker compose setup. 

#### 5. Inspect it

Visit

http://basyx-starter-app:8090/swagger-ui/index.html

Then, you should see not only the endpoints of the dsp-protocol-library but also the endpoints that are provided by BaSyx, 
which allow you to store, retrieve, update and delete Asset Administration Shells and/or Submodels. 

#### 6. Use the DSP/DCP stack

Import the attached Postman Collection, then you can

- create an Asset Administration Shells and a Submodel on BaSyx
- have the consumer control plane negotiate and transfer it. 





