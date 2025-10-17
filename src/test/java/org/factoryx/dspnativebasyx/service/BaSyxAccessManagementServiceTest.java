package org.factoryx.dspnativebasyx.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.eclipse.digitaltwin.aas4j.v3.model.*;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.*;
import org.eclipse.digitaltwin.basyx.aasrepository.AasRepository;
import org.eclipse.digitaltwin.basyx.aasrepository.feature.authorization.AasTargetInformation;
import org.eclipse.digitaltwin.basyx.aasservice.backend.AasBackend;
import org.eclipse.digitaltwin.basyx.authorization.rbac.Action;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacRule;
import org.eclipse.digitaltwin.basyx.authorization.rbac.RbacStorage;
import org.eclipse.digitaltwin.basyx.authorization.rules.rbac.backend.inmemory.InMemoryAuthorizationRbacStorage;
import org.eclipse.digitaltwin.basyx.submodelrepository.SubmodelRepository;
import org.eclipse.digitaltwin.basyx.submodelservice.backend.SubmodelBackend;
import org.eclipse.digitaltwin.basyx.submodelservice.feature.authorization.SubmodelTargetInformation;
import org.factoryx.library.connector.embedded.provider.interfaces.DataAsset;
import org.factoryx.library.connector.embedded.provider.interfaces.DspTokenValidationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class tests the BaSyxAccessManagementService as well as the RbacDCPValidationService
 */
public class BaSyxAccessManagementServiceTest {

    @Mock
    private AasBackend aasBackend;
    @Mock
    private SubmodelBackend submodelBackend;
    @Mock
    private AasRepository aasRepository;
    @Mock
    private SubmodelRepository submodelRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new SimpleModule()
                    .addAbstractTypeMapping(AssetInformation.class, DefaultAssetInformation.class)
                    .addAbstractTypeMapping(Reference.class, DefaultReference.class)
                    .addAbstractTypeMapping(Key.class, DefaultKey.class)
                    .addAbstractTypeMapping(Resource.class, DefaultResource.class)
            )
            .build();

    private BaSyxAccessManagementService baSyxAccessManagementService;

    private final RbacRule shellReadRule = new RbacRule(TRUSTED_BUSINESS_PARTNER, List.of(Action.READ), new AasTargetInformation(List.of("*")));

    private final RbacRule submodelReadRule = new RbacRule(TRUSTED_BUSINESS_PARTNER, List.of(Action.READ), new SubmodelTargetInformation(List.of("*"), List.of("*")));

    private final AssetAdministrationShell shell = getShell();
    private final Submodel submodel = getSubmodel();

    static String TRUSTED_BUSINESS_PARTNER = "trusted_business_partner";
    static String DATASPACE_MEMBER = "dataspacemember";

    static final Map<String, String> happyCasePartnerProperties = Map.of(DspTokenValidationService.ReservedKeys.credentials.toString(), "dataspacemember");
    static final Map<String, String> insufficientPartnerProperties = Map.of(DspTokenValidationService.ReservedKeys.credentials.toString(), "something-wrong");

    private final String shellId = "test-shell-id";
    private final String submodelId = "test-submodel-id";
    private String shellsApiAssetId;
    private String submodelsApiAssetId;


    @BeforeEach
    public void init() throws Exception {
        MockitoAnnotations.openMocks(this);
        RbacStorage rbacStorage = new InMemoryAuthorizationRbacStorage(new HashMap<>());
        rbacStorage.addRule(shellReadRule);
        rbacStorage.addRule(submodelReadRule);
        Mockito.when(aasRepository.getName()).thenReturn("aas-repository");
        Mockito.when(submodelRepository.getName()).thenReturn("submodel-repository");
        MessagingSupportService messagingSupportService = new MessagingSupportService(Optional.empty(),
                Optional.empty(), Optional.empty(), aasRepository, submodelRepository, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), objectMapper);
        RbacDCPValidationService rbacDCPValidationService =
                new RbacDCPValidationService(rbacStorage, DATASPACE_MEMBER + "=" + TRUSTED_BUSINESS_PARTNER);
        baSyxAccessManagementService =
                new BaSyxAccessManagementService(aasBackend, submodelBackend, rbacDCPValidationService, objectMapper, messagingSupportService, "", "");
        Field shellsApiAssetIdField = BaSyxAccessManagementService.class.getDeclaredField("SHELLS_API_ASSET_ID");
        shellsApiAssetIdField.setAccessible(true);
        shellsApiAssetId = (String) shellsApiAssetIdField.get(baSyxAccessManagementService);
        Field submodelsApiAssetIdField = BaSyxAccessManagementService.class.getDeclaredField("SUBMODELS_API_ASSET_ID");
        submodelsApiAssetIdField.setAccessible(true);
        submodelsApiAssetId = (String) submodelsApiAssetIdField.get(baSyxAccessManagementService);
    }


    @Test
    public void getAllShouldReturnExpectedAssets() {
        Mockito.when(aasBackend.findAll()).thenReturn(List.of(shell));
        Mockito.when(submodelBackend.findAll()).thenReturn(List.of(submodel));

        List<DataAsset> resultList = baSyxAccessManagementService.getAll(happyCasePartnerProperties);
        var expectedIds = List.of(shellId, submodelId, shellsApiAssetId, submodelsApiAssetId);
        Assertions.assertTrue(resultList.stream().allMatch(dataAsset -> expectedIds.contains(dataAsset.getNativeId())));
    }

    @Test
    public void getByIdShouldReturnExpectedAsset() {
        Mockito.when(aasBackend.findById(shellId)).thenReturn(Optional.of(shell));
        Mockito.when(submodelBackend.findById(submodelId)).thenReturn(Optional.of(submodel));

        DataAsset shellDataAsset = baSyxAccessManagementService.getById(shellId);
        DataAsset submodelDataAsset = baSyxAccessManagementService.getById(submodelId);
        Assertions.assertNotNull(shellDataAsset);
        Assertions.assertEquals(shellId, shellDataAsset.getDspId());
        Assertions.assertNotNull(submodelDataAsset);
        Assertions.assertEquals(submodelId, submodelDataAsset.getDspId());
    }

    @Test
    public void getByIdShouldReturnNotFound() {
        String falseId = "falseId";
        Mockito.when(aasBackend.findById(falseId)).thenReturn(Optional.empty());

        DataAsset expectedNotFound = baSyxAccessManagementService.getById(falseId);
        Assertions.assertNull(expectedNotFound);
    }

    @Test
    public void getByIdForPropertiesShouldReturnExpectedAsset() {
        Mockito.when(aasBackend.findById(shellId)).thenReturn(Optional.of(shell));
        Mockito.when(submodelBackend.findById(submodelId)).thenReturn(Optional.of(submodel));

        DataAsset shellDataAsset = baSyxAccessManagementService.getByIdForProperties(shellId, happyCasePartnerProperties);
        DataAsset submodelDataAsset = baSyxAccessManagementService.getByIdForProperties(submodelId, happyCasePartnerProperties);
        Assertions.assertNotNull(shellDataAsset);
        Assertions.assertEquals(shellId, shellDataAsset.getDspId());
        Assertions.assertNotNull(submodelDataAsset);
        Assertions.assertEquals(submodelId, submodelDataAsset.getDspId());
    }

    @Test
    public void getByIdForPropertiesShouldReturnNotFound() {
        Mockito.when(aasBackend.findById(shellId)).thenReturn(Optional.of(shell));
        Mockito.when(submodelBackend.findById(submodelId)).thenReturn(Optional.of(submodel));

        DataAsset expectedShellNotFound = baSyxAccessManagementService.getByIdForProperties(shellId, insufficientPartnerProperties);
        DataAsset expectedSubmodelNotFound = baSyxAccessManagementService.getByIdForProperties(submodelId, insufficientPartnerProperties);
        Assertions.assertNull(expectedShellNotFound);
        Assertions.assertNull(expectedSubmodelNotFound);
    }

    @Test
    public void testApiAssetShouldReturn201() throws IOException {
        AssetAdministrationShell localShell = objectMapper.readValue(jsonShell.getBytes(), DefaultAssetAdministrationShell.class);
        Mockito.when(aasBackend.save(Mockito.any())).thenReturn(localShell);
        ResponseEntity<byte[]> response = baSyxAccessManagementService.forwardToApiAsset(shellsApiAssetId, HttpMethod.POST, jsonShell.getBytes(), HttpHeaders.EMPTY, "/shells", MultiValueMap.fromSingleValue(Map.of()));

        Assertions.assertNotNull(response);
        Assertions.assertEquals(201, response.getStatusCode().value());
        AssetAdministrationShell shellFromResponse = objectMapper.readValue(response.getBody(), DefaultAssetAdministrationShell.class);
        Assertions.assertEquals(localShell, shellFromResponse);
    }

    @Test
    public void testApiAssetShouldReturn401() throws IOException {
        AssetAdministrationShell localShell = objectMapper.readValue(jsonShell.getBytes(), DefaultAssetAdministrationShell.class);
        Mockito.when(aasBackend.save(Mockito.any())).thenReturn(localShell);
        ResponseEntity<byte[]> response = baSyxAccessManagementService.forwardToApiAsset(submodelsApiAssetId, HttpMethod.POST, jsonShell.getBytes(), HttpHeaders.EMPTY, "/shells", MultiValueMap.fromSingleValue(Map.of()));

        Assertions.assertNotNull(response);
        Assertions.assertEquals(401, response.getStatusCode().value());
    }




    /* *** SAMPLE DATA *** */

    private AssetAdministrationShell getShell() {
        AssetAdministrationShell shell = new DefaultAssetAdministrationShell();
        AssetInformation assetInformation = new DefaultAssetInformation();
        assetInformation.setGlobalAssetId("global-" + shellId);
        shell.setAssetInformation(assetInformation);
        shell.setId(shellId);
        return shell;
    }

    private Submodel getSubmodel() {
        Submodel submodel = new DefaultSubmodel();
        submodel.setId(submodelId);
        return submodel;
    }


    private static final String jsonShell = """
            {
              "modelType": "AssetAdministrationShell",
              "assetInformation": {
                "assetKind": "Instance",
                "assetType": "Car",
                "defaultThumbnail": {
                  "contentType": "",
                  "path": ""
                },
                "globalAssetId": "urn:uuid:580d3adf-1981-44a0-a214-13d6ceed9379"
              },
              "submodels": [
                {
                  "keys": [
                    {
                      "type": "Submodel",
                      "value": "fe6229e6-abcd-4c6e-809b-db59e70f7c6e"
                    }
                  ],
                  "type": "External_Reference"
                }
              ],
              "id": "e38a0f92-4ee8-436c-a535-0f9cdad4c3c8",
              "idShort": "Car"
            }
            """;

}
