// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.datafactory.generated;

import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.datafactory.models.SalesforceServiceCloudV2Source;

public final class SalesforceServiceCloudV2SourceTests {
    @org.junit.jupiter.api.Test
    public void testDeserialize() throws Exception {
        SalesforceServiceCloudV2Source model = BinaryData.fromString(
            "{\"type\":\"SalesforceServiceCloudV2Source\",\"SOQLQuery\":\"datahrzpyxmfip\",\"includeDeletedObjects\":\"datamlf\",\"additionalColumns\":\"datawfxssxarxvft\",\"sourceRetryCount\":\"datasuqap\",\"sourceRetryWait\":\"datadgrbcltfkyq\",\"maxConcurrentConnections\":\"dataiujukcdlvpt\",\"disableMetricsCollection\":\"dataycupmfp\",\"\":{\"pxslccu\":\"dataswgnglmllr\",\"ndirdlehjz\":\"datascjefapouwsynsb\",\"kt\":\"datapdwyhggvhcoaoeti\",\"ae\":\"dataeirambfm\"}}")
            .toObject(SalesforceServiceCloudV2Source.class);
    }

    @org.junit.jupiter.api.Test
    public void testSerialize() throws Exception {
        SalesforceServiceCloudV2Source model = new SalesforceServiceCloudV2Source().withSourceRetryCount("datasuqap")
            .withSourceRetryWait("datadgrbcltfkyq").withMaxConcurrentConnections("dataiujukcdlvpt")
            .withDisableMetricsCollection("dataycupmfp").withSoqlQuery("datahrzpyxmfip")
            .withIncludeDeletedObjects("datamlf").withAdditionalColumns("datawfxssxarxvft");
        model = BinaryData.fromObject(model).toObject(SalesforceServiceCloudV2Source.class);
    }
}
