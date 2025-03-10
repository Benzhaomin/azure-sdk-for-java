// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.datafactory.generated;

import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.datafactory.models.OdbcSink;

public final class OdbcSinkTests {
    @org.junit.jupiter.api.Test
    public void testDeserialize() throws Exception {
        OdbcSink model = BinaryData.fromString(
            "{\"type\":\"OdbcSink\",\"preCopyScript\":\"datam\",\"writeBatchSize\":\"databfzaaiihyl\",\"writeBatchTimeout\":\"datazhlbpmplethek\",\"sinkRetryCount\":\"datanamtvooaace\",\"sinkRetryWait\":\"dataonsvjc\",\"maxConcurrentConnections\":\"datatytyrv\",\"disableMetricsCollection\":\"dataxvzywimmmmg\",\"\":{\"nvahpxdgy\":\"datavoytdtvkfq\",\"ygc\":\"dataowxcptxvxfwwv\",\"jri\":\"dataaztoias\"}}")
            .toObject(OdbcSink.class);
    }

    @org.junit.jupiter.api.Test
    public void testSerialize() throws Exception {
        OdbcSink model = new OdbcSink().withWriteBatchSize("databfzaaiihyl").withWriteBatchTimeout("datazhlbpmplethek")
            .withSinkRetryCount("datanamtvooaace").withSinkRetryWait("dataonsvjc")
            .withMaxConcurrentConnections("datatytyrv").withDisableMetricsCollection("dataxvzywimmmmg")
            .withPreCopyScript("datam");
        model = BinaryData.fromObject(model).toObject(OdbcSink.class);
    }
}
