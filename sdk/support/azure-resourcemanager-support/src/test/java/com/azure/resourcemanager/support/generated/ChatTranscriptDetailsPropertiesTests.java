// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.support.generated;

import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.support.fluent.models.ChatTranscriptDetailsProperties;
import com.azure.resourcemanager.support.models.MessageProperties;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;

public final class ChatTranscriptDetailsPropertiesTests {
    @org.junit.jupiter.api.Test
    public void testDeserialize() throws Exception {
        ChatTranscriptDetailsProperties model = BinaryData.fromString(
            "{\"messages\":[{\"communicationDirection\":\"inbound\",\"sender\":\"tdtkcn\",\"body\":\"xwbpokulpiuj\",\"createdDate\":\"2021-01-28T10:46:40Z\"},{\"communicationDirection\":\"outbound\",\"sender\":\"i\",\"body\":\"obyu\",\"createdDate\":\"2021-11-02T07:24:48Z\"}],\"startTime\":\"2021-08-02T06:07:15Z\"}")
            .toObject(ChatTranscriptDetailsProperties.class);
        Assertions.assertEquals("tdtkcn", model.messages().get(0).sender());
        Assertions.assertEquals("xwbpokulpiuj", model.messages().get(0).body());
    }

    @org.junit.jupiter.api.Test
    public void testSerialize() throws Exception {
        ChatTranscriptDetailsProperties model = new ChatTranscriptDetailsProperties()
            .withMessages(Arrays.asList(new MessageProperties().withSender("tdtkcn").withBody("xwbpokulpiuj"),
                new MessageProperties().withSender("i").withBody("obyu")));
        model = BinaryData.fromObject(model).toObject(ChatTranscriptDetailsProperties.class);
        Assertions.assertEquals("tdtkcn", model.messages().get(0).sender());
        Assertions.assertEquals("xwbpokulpiuj", model.messages().get(0).body());
    }
}
