package org.factoryx.dspnativebasyx.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;

import java.util.Map;
import java.util.UUID;

/**
 * This class wraps an AssetAdministrationShell object from the BaSyx world
 * and maps it into a DataAsset which can be forwarded to the dsp-protocol-lib.
 *
 */
public class AasDataAsset implements DataAsset {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private final AssetAdministrationShell asset;
    private final UUID uuid;
    public AasDataAsset(AssetAdministrationShell asset, UUID uuid) {
        this.asset = asset;
        this.uuid = uuid;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of("modelType", "AssetAdministrationShell",
                "globalAssetId", asset.getAssetInformation().getGlobalAssetId(),
                "idShort", asset.getIdShort(),
                "dto-type", getContentType());
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public byte[] getDtoRepresentation() {
        try {
            return MAPPER.writeValueAsBytes(asset);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
