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

Run a small testing environment that includes FX EDC Controlplane as a customer partner.

#### Start it

```
docker compose up
```

Or use the shell script 

```
bash rundocker.sh
```

Hint: You may need to obtain the necessary identity configuration, see the docker-compose.yaml and 
consumer-controlplane.env files.  


#### Inspect it

Visit

http://basyx-starter-app:8090/swagger-ui/index.html

Then, you should see not only the endpoints of the dsp-protocol-library but also the endpoints that are provided by BaSyx, 
which allow you to store, retrieve, update and delete Asset Administration Shells and/or Submodels. 

#### Use the DSP/DCP stack

Import the attached Bruno Collection, then you can

- create an Asset Administration Shells and a Submodel on BaSyx
- have the consumer control plane negotiate and transfer it. 

Please note that the Postman collection is deprecated, will no longer be updated and may be removed in the future. 





