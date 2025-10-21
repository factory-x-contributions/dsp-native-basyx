package org.factoryx.dspnativebasyx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.basyx.aasregistry.client.ApiException;
import org.eclipse.digitaltwin.basyx.aasregistry.client.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.basyx.aasregistry.main.client.factory.AasDescriptorFactory;
import org.eclipse.digitaltwin.basyx.aasregistry.main.client.mapper.AttributeMapper;
import org.eclipse.digitaltwin.basyx.aasrepository.AasRepository;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.kafka.events.AasEventHandler;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.mqtt.MqttAasRepositoryTopicFactory;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.registry.integration.AasRepositoryRegistryLink;
import org.eclipse.digitaltwin.basyx.common.mqttcore.encoding.Base64URLEncoder;
import org.eclipse.digitaltwin.basyx.submodelregistry.client.factory.SubmodelDescriptorFactory;
import org.eclipse.digitaltwin.basyx.submodelregistry.client.model.SubmodelDescriptor;
import org.eclipse.digitaltwin.basyx.submodelrepository.SubmodelRepository;
import org.eclipse.digitaltwin.basyx.submodelrepository.feature.mqtt.MqttSubmodelRepositoryTopicFactory;
import org.eclipse.digitaltwin.basyx.submodelrepository.feature.registry.integration.SubmodelRepositoryRegistryLink;
import org.eclipse.digitaltwin.basyx.submodelservice.feature.kafka.events.SubmodelEventHandler;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * This service receives notifications on events that are happening
 * within the context of the ApiAssets and if necessary issues
 * corresponding messages to related services.
 *
 * Currently supported is: MQTT, Kafka and BaSyx registry.
 */
@Service
@Slf4j
public class MessagingSupportService {

    private final ObjectMapper objectMapper;
    private final SubmodelEventHandler submodelEventHandler;
    private final AasEventHandler aasEventHandler;

    private final IMqttClient mqttClient;
    private final MqttSubmodelRepositoryTopicFactory submodelTopicFactory = new MqttSubmodelRepositoryTopicFactory(new Base64URLEncoder());
    private final MqttAasRepositoryTopicFactory aasTopicFactory = new MqttAasRepositoryTopicFactory(new Base64URLEncoder());
    private final String aasRepoName;
    private final String submodelRepoName;

    private final AasRepositoryRegistryLink aasRepositoryRegistryLink;
    private final AasDescriptorFactory aasDescriptorFactory;

    private final SubmodelRepositoryRegistryLink submodelRepositoryRegistryLink;
    private final SubmodelDescriptorFactory submodelDescriptorFactory;

    static final ExecutorService executorService = Executors.newCachedThreadPool();


    public MessagingSupportService(Optional<SubmodelEventHandler> submodelEventHandler, Optional<AasEventHandler> aasEventHandler,
                                   Optional<IMqttClient> iMqttClient, AasRepository aasRepository, SubmodelRepository submodelRepo,
                                   Optional<AasRepositoryRegistryLink> aasRepositoryRegistryLink, Optional<AttributeMapper> attributeMapper,
                                   Optional<SubmodelRepositoryRegistryLink> submodelRepositoryRegistryLink,
                                   Optional<org.eclipse.digitaltwin.basyx.submodelregistry.client.mapper.AttributeMapper> submodelAttributeMapper, ObjectMapper objectMapper) {
        this.submodelEventHandler = submodelEventHandler.orElse(null);
        this.aasEventHandler = aasEventHandler.orElse(null);
        this.mqttClient = iMqttClient.orElse(null);
        this.aasRepoName = aasRepository.getName();
        this.submodelRepoName = submodelRepo.getName();
        this.aasRepositoryRegistryLink = aasRepositoryRegistryLink.orElse(null);
        this.objectMapper = objectMapper;
        if (this.aasRepositoryRegistryLink != null && attributeMapper.isPresent()) {
            this.aasDescriptorFactory = new AasDescriptorFactory(this.aasRepositoryRegistryLink.getAasRepositoryBaseURLs(), attributeMapper.get());
        } else {
            this.aasDescriptorFactory = null;
        }
        this.submodelRepositoryRegistryLink = submodelRepositoryRegistryLink.orElse(null);
        if (this.submodelRepositoryRegistryLink != null && submodelAttributeMapper.isPresent()) {
            this.submodelDescriptorFactory = new SubmodelDescriptorFactory(this.submodelRepositoryRegistryLink.getSubmodelRepositoryBaseURLs(), submodelAttributeMapper.get());
        } else {
            this.submodelDescriptorFactory = null;
        }
    }

    public void notifyNewShell(AssetAdministrationShell shell) {
        if (aasEventHandler != null) {
            executorService.submit(() -> aasEventHandler.onAasCreated(shell));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(aasTopicFactory.createCreateAASTopic(aasRepoName), shell));
        }
        if (aasDescriptorFactory != null) {
            executorService.submit(() -> registerAas(shell));
        }
    }


    public void notifyUpdatedShell(AssetAdministrationShell shell) {
        if (aasEventHandler != null) {
            executorService.submit(() -> aasEventHandler.onAasUpdated(shell.getId(), shell));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(aasTopicFactory.createUpdateAASTopic(aasRepoName), shell));
        }
    }

    public void notifyDeletedShell(AssetAdministrationShell shell) {
        if (aasEventHandler != null) {
            executorService.submit(() -> aasEventHandler.onAasDeleted(shell.getId()));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(aasTopicFactory.createDeleteAASTopic(aasRepoName), shell));
        }
        if (aasRepositoryRegistryLink != null) {
            executorService.submit(() -> {
                try {
                    aasRepositoryRegistryLink.getRegistryApi().deleteAssetAdministrationShellDescriptorById(shell.getId());
                } catch (ApiException e) {
                    log.error("Error registering shell deletion {}", shell.getId(), e);
                }
            });
        }
    }


    public void notifyNewSubmodel(Submodel submodel) {
        if (submodelEventHandler != null) {
            executorService.submit(() -> submodelEventHandler.onSubmodelCreated(submodel));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createCreateSubmodelTopic(submodelRepoName), submodel));
        }
        if (submodelDescriptorFactory != null) {
            executorService.submit(() -> registerSubmodel(submodel));
        }
    }


    public void notifyUpdatedSubmodel(Submodel submodel) {
        if (submodelEventHandler != null) {
            final Submodel finalSubmodel = submodel;
            executorService.submit(() -> submodelEventHandler.onSubmodelUpdated(finalSubmodel));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createUpdateSubmodelTopic(submodelRepoName), submodel));
        }
    }

    public void notifyDeletedSubmodel(Submodel submodel) {
        if (submodelEventHandler != null) {
            executorService.submit(() -> submodelEventHandler.onSubmodelDeleted(submodel.getId()));
        }
        if (mqttClient != null) {
            executorService.submit(() -> sendMqttMessage(submodelTopicFactory.createDeleteSubmodelTopic(submodelRepoName), submodel));
        }
        if (submodelRepositoryRegistryLink != null) {
            executorService.submit(() -> {
                try {
                    submodelRepositoryRegistryLink.getRegistryApi().deleteSubmodelDescriptorById(submodel.getId());
                } catch (org.eclipse.digitaltwin.basyx.submodelregistry.client.ApiException e) {
                    log.error("Error registering submodel deletion {}", submodel.getId(), e);
                }
            });
        }
    }


    private void sendMqttMessage(String topic, Object payload) {
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            MqttMessage msg = payloadBytes == null ? new MqttMessage() : new MqttMessage(payloadBytes);
            mqttClient.publish(topic, msg);
            log.info("Sent MQTT message about topic {} with payload: {}", topic, payload);
        } catch (MqttPersistenceException e) {
            log.error("Could not persist mqtt message", e);
        } catch (Exception e) {
            log.error("Could not send mqtt message", e);
        }
    }

    private void registerAas(AssetAdministrationShell shell) {
        try {
            AssetAdministrationShellDescriptor descriptor = aasDescriptorFactory.create(shell);
            aasRepositoryRegistryLink.getRegistryApi().postAssetAdministrationShellDescriptor(descriptor);
            log.info("Shell '{}' has been automatically linked with the Registry", descriptor.getId());
        } catch (ApiException e) {
            log.error("Failed to automatically link shell descriptor", e);
        }
    }

    private void registerSubmodel(Submodel submodel) {
        try {
            SubmodelDescriptor descriptor = submodelDescriptorFactory.create(submodel);
            submodelRepositoryRegistryLink.getRegistryApi().postSubmodelDescriptor(descriptor);
            log.info("Submodel '{}' has been automatically linked with the Registry", descriptor.getId());
        } catch (Exception e) {
            log.error("Failed to automatically link submodel", e);
        }
    }
}
