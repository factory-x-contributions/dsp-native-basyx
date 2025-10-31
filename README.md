# BaSyx-Starter-App

## Introduction

This repository contains a DSP-native variant of the Eclipse BaSyx Asset Administration Shell (AAS) system, built on the Spring Boot framework. It integrates the Dataspace Protocol (DSP) library along with selected components from the [Eclipse BaSyx framework](https://mvnrepository.com/artifact/org.eclipse.digitaltwin.basyx), enabling seamless interoperability within dataspace environments.

## Contributions:
In order to contribute to the project, please look at the [CONTRIBUTING.md](/CONTRIBUTING.md) file for the contribution guidelines. 

## Installation


This application can be run in various environments depending on deployment environments. This sections introduces the basic steps to run the application in a basic scenario.

### Dependency Management
The DSP-Native-BaSyx depends on the [dataspace protocol java library](https://github.com/factory-x-contributions/dataspace-protocol-lib). The dependency is available through the GitHub package registry.

In order to access the dependency, you need to authenticate against the GitHub package registry by adding the following server login credentials to your Maven Settings XML file.

```xml
<servers>
    <server>
        <id>github</id>
        <username>${env.GITHUB_ACTOR}</username>
        <password>${env.GITHUB_TOKEN}</password>
    </server>
</servers>
```
> Information on how to find and update the Maven Settings file can be found [here](https://maven.apache.org/settings.html)

The actual credentials are read from the system environment variables by setting the required GitHub Username `GITHUB_ACTOR` and Access Token `GITHUB_TOKEN` values. 

```bash
export GITHUB_ACTOR=<username>
export GITHUB_TOKEN=<access-token>
```


### Build Process

After setting the correct GitHub credentials in the environment variables, the build process can be started. 

#### Package Creation

This process will build a runnable jar file. This can be done by using the included Maven wrapper to create a .jar file in the within the project's `target`` folder: 

```
./mvnw clean package
```

### Dockerization

A docker container can be built using the provided docker file. The Dockerfile assumes that you have already built a .jar file in the previous step. 

```
docker build -t basyxstarterapp .
```

### Docker Compose Deployment

We provide a docker compose file to run a small testing environment that includes FX EDC Controlplane as a customer partner.

#### Start it

```
docker compose up
```

Or use the shell script, which has additional "nice-to-have" functionalities. 

```
bash rundocker.sh
```

> Hint: You may need to obtain the necessary identity configuration, see the docker-compose.yaml and 
consumer-controlplane.env files.  


#### Use 

After running the docker 

http://basyx-starter-app:8090/swagger-ui/index.html

Then, you should see not only the endpoints of the dsp-protocol-library but also the endpoints that are provided by BaSyx, 
which allow you to store, retrieve, update and delete Asset Administration Shells and/or Submodels. 

#### Use the DSP/DCP stack

Import the attached Bruno Collection, then you can

- create an Asset Administration Shells and a Submodel on BaSyx
- have the consumer control plane negotiate and transfer it. 


## License
Distributed under the Apache 2.0 License.
See [LICENSE](./LICENSE) for more information.