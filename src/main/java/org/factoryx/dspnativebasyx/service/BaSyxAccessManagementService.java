package org.factoryx.dspnativebasyx.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.digitaltwin.basyx.aasservice.backend.AasBackend;
import org.eclipse.digitaltwin.basyx.submodelservice.backend.SubmodelBackend;
import org.factoryx.dspnativebasyx.model.AasDataAsset;
import org.factoryx.dspnativebasyx.model.SubmodelDataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAssetManagementService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
/**
 * Implementation that provides access for the dsp-protocol-lib to the contents of
 * the AasBackend and SubmodelBackend from BaSyx.
 */
public class BaSyxAccessManagementService implements DataAssetManagementService {

    private final AasBackend aasBackend;
    private final SubmodelBackend submodelBackend;

    // provisional mapping helpers in order to cope with the fact that the dsp-protocol-lib
    // currently works with UUID typed identfiers and BaSyx with Strings.
    private Map<String, UUID> basyxIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> submodelIdsToUuidMapping = new ConcurrentHashMap<>();
    private Map<UUID, String> aasIdsToUuidMapping = new ConcurrentHashMap<>();


    public BaSyxAccessManagementService(AasBackend aasBackend, SubmodelBackend submodelBackend) {
        this.aasBackend = aasBackend;
        this.submodelBackend = submodelBackend;
    }


    @Override
    public DataAsset getById(UUID id) {
        // TODO: we should modify the dsp-protocol-lib so that this method also gets credential
        // information (passed on from the DspValidationService) as a parameter in order to allow us here
        // implement a mapping from these credential findings to the roles
        if (aasIdsToUuidMapping.containsKey(id)) {
            var aasOptional = aasBackend.findById(aasIdsToUuidMapping.get(id));
            if (aasOptional.isPresent()) {
                return new AasDataAsset(aasOptional.get(), id);
            }
        } else if (submodelIdsToUuidMapping.containsKey(id)) {
            var submodelOptional = submodelBackend.findById(submodelIdsToUuidMapping.get(id));
            if (submodelOptional.isPresent()) {
                return new SubmodelDataAsset(submodelOptional.get(), id);
            }
        }
        log.warn("No such id {}", id);
        return null;
    }

    @Override
    public List<? extends DataAsset> getAll() {
        // TODO: we should modify the dsp-protocol-lib so that this method also gets credential
        // information (passed on from the DspValidationService) as a parameter in order to allow us here
        // implement a mapping from these credential findings to the roles
        ArrayList<DataAsset> dataAssets = new ArrayList<>();
        aasBackend.findAll().forEach(asset -> {
            UUID uuid = basyxIdsToUuidMapping.computeIfAbsent(asset.getId(), any -> UUID.randomUUID());
            aasIdsToUuidMapping.put(uuid, asset.getId());
            dataAssets.add(new AasDataAsset(asset, uuid));
        });

        submodelBackend.findAll().forEach(submodel -> {
            UUID uuid = basyxIdsToUuidMapping.computeIfAbsent(submodel.getId(), any -> UUID.randomUUID());
            submodelIdsToUuidMapping.put(uuid, submodel.getId());
            dataAssets.add(new SubmodelDataAsset(submodel, uuid));
        });
        return dataAssets;

    }
}
