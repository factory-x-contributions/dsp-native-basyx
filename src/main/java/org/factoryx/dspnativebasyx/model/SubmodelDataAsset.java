package org.factoryx.dspnativebasyx.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.digitaltwin.aas4j.v3.model.Referable;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.factoryx.dspnativebasyx.model.AasDataAsset.MAPPER;

public class SubmodelDataAsset implements DataAsset {
    private final Submodel submodel;
    private final UUID uuid;

    public SubmodelDataAsset(Submodel submodel, UUID uuid) {
        this.submodel = submodel;
        this.uuid = uuid;
    }


    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of("modelType", "Submodel",
                "submodelId", submodel.getId(),
                "idShort", submodel.getIdShort(),
                "dto-type", getContentType());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] getDtoRepresentation() {
        try {
            ObjectNode node = MAPPER.convertValue(submodel, ObjectNode.class);
            node.put("modelType", "Submodel");
            return node.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSubmodelId() {
        return submodel.getId();
    }

    public List<String> getSubmodelElements(){
        return submodel.getSubmodelElements().stream().map(Referable::getIdShort).toList();
    }

}
