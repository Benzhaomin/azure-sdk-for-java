// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.support.generated;

import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.support.models.SecondaryConsent;
import com.azure.resourcemanager.support.models.UserConsent;
import org.junit.jupiter.api.Assertions;

public final class SecondaryConsentTests {
    @org.junit.jupiter.api.Test
    public void testDeserialize() throws Exception {
        SecondaryConsent model = BinaryData.fromString("{\"userConsent\":\"No\",\"type\":\"bgycduiertgccym\"}")
            .toObject(SecondaryConsent.class);
        Assertions.assertEquals(UserConsent.NO, model.userConsent());
        Assertions.assertEquals("bgycduiertgccym", model.type());
    }

    @org.junit.jupiter.api.Test
    public void testSerialize() throws Exception {
        SecondaryConsent model = new SecondaryConsent().withUserConsent(UserConsent.NO).withType("bgycduiertgccym");
        model = BinaryData.fromObject(model).toObject(SecondaryConsent.class);
        Assertions.assertEquals(UserConsent.NO, model.userConsent());
        Assertions.assertEquals("bgycduiertgccym", model.type());
    }
}
