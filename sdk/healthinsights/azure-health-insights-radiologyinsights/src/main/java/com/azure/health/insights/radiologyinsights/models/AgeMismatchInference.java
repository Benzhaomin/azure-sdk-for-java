// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) TypeSpec Code Generator.
package com.azure.health.insights.radiologyinsights.models;

import com.azure.core.annotation.Generated;
import com.azure.core.annotation.Immutable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A notification for age mismatch is displayed when the age mentioned in a document for a specific patient does not
 * match the age specified in the patient information.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", defaultImpl = AgeMismatchInference.class, visible = true)
@JsonTypeName("ageMismatch")
@Immutable
public final class AgeMismatchInference extends RadiologyInsightsInference {

    /*
     * Inference type.
     */
    @Generated
    @JsonTypeId
    @JsonProperty(value = "kind")
    private RadiologyInsightsInferenceType kind = RadiologyInsightsInferenceType.AGE_MISMATCH;

    /**
     * Creates an instance of AgeMismatchInference class.
     */
    @Generated
    private AgeMismatchInference() {
    }

    /**
     * Get the kind property: Inference type.
     *
     * @return the kind value.
     */
    @Generated
    @Override
    public RadiologyInsightsInferenceType getKind() {
        return this.kind;
    }
}
