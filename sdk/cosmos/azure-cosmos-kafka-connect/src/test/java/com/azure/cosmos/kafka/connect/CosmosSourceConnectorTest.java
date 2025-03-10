// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.kafka.connect;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.implementation.ImplementationBridgeHelpers;
import com.azure.cosmos.implementation.Strings;
import com.azure.cosmos.implementation.Utils;
import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.azure.cosmos.implementation.changefeed.common.ChangeFeedMode;
import com.azure.cosmos.implementation.changefeed.common.ChangeFeedStartFromInternal;
import com.azure.cosmos.implementation.changefeed.common.ChangeFeedState;
import com.azure.cosmos.implementation.changefeed.common.ChangeFeedStateV1;
import com.azure.cosmos.implementation.feedranges.FeedRangeContinuation;
import com.azure.cosmos.implementation.feedranges.FeedRangeEpkImpl;
import com.azure.cosmos.implementation.query.CompositeContinuationToken;
import com.azure.cosmos.kafka.connect.implementation.CosmosAuthTypes;
import com.azure.cosmos.kafka.connect.implementation.CosmosClientStore;
import com.azure.cosmos.kafka.connect.implementation.source.CosmosChangeFeedModes;
import com.azure.cosmos.kafka.connect.implementation.source.CosmosChangeFeedStartFromModes;
import com.azure.cosmos.kafka.connect.implementation.source.CosmosSourceConfig;
import com.azure.cosmos.kafka.connect.implementation.source.CosmosSourceOffsetStorageReader;
import com.azure.cosmos.kafka.connect.implementation.source.CosmosSourceTask;
import com.azure.cosmos.kafka.connect.implementation.source.FeedRangeContinuationTopicOffset;
import com.azure.cosmos.kafka.connect.implementation.source.FeedRangeContinuationTopicPartition;
import com.azure.cosmos.kafka.connect.implementation.source.FeedRangeTaskUnit;
import com.azure.cosmos.kafka.connect.implementation.source.FeedRangesMetadataTopicOffset;
import com.azure.cosmos.kafka.connect.implementation.source.FeedRangesMetadataTopicPartition;
import com.azure.cosmos.kafka.connect.implementation.source.KafkaCosmosChangeFeedState;
import com.azure.cosmos.kafka.connect.implementation.source.MetadataMonitorThread;
import com.azure.cosmos.kafka.connect.implementation.source.MetadataTaskUnit;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.FeedRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigValue;
import org.apache.kafka.common.config.types.Password;
import org.apache.kafka.connect.source.SourceConnectorContext;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.testng.Assert.assertEquals;

@Test
public class CosmosSourceConnectorTest extends KafkaCosmosTestSuiteBase {
    @Test(groups = "unit")
    public void taskClass() {
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        assertEquals(sourceConnector.taskClass(), CosmosSourceTask.class);
    }

    @Test(groups = "unit")
    public void config() {
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        ConfigDef configDef = sourceConnector.config();
        Map<String, ConfigDef.ConfigKey> configs = configDef.configKeys();
        List<KafkaCosmosConfigEntry<?>> allValidConfigs = CosmosSourceConnectorTest.SourceConfigs.ALL_VALID_CONFIGS;

        for (KafkaCosmosConfigEntry<?> sourceConfigEntry : allValidConfigs) {
            assertThat(configs.containsKey(sourceConfigEntry.getName())).isTrue();

            configs.containsKey(sourceConfigEntry.getName());
            if (sourceConfigEntry.isOptional()) {
                if (sourceConfigEntry.isPassword()) {
                    assertThat(((Password)configs.get(sourceConfigEntry.getName()).defaultValue).value())
                        .isEqualTo(sourceConfigEntry.getDefaultValue());
                } else {
                    assertThat(configs.get(sourceConfigEntry.getName()).defaultValue).isEqualTo(sourceConfigEntry.getDefaultValue());
                }
            } else {
                assertThat(configs.get(sourceConfigEntry.getName()).defaultValue).isEqualTo(ConfigDef.NO_DEFAULT_VALUE);
            }
        }
    }

    @Test(groups = "{ kafka }", timeOut = TIMEOUT)
    public void getTaskConfigsWithoutPersistedOffset() throws JsonProcessingException {
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        try {
            Map<String, Object> sourceConfigMap = new HashMap<>();
            sourceConfigMap.put("kafka.connect.cosmos.accountEndpoint", KafkaCosmosTestConfigurations.HOST);
            sourceConfigMap.put("kafka.connect.cosmos.accountKey", KafkaCosmosTestConfigurations.MASTER_KEY);
            sourceConfigMap.put("kafka.connect.cosmos.source.database.name", databaseName);
            List<String> containersIncludedList = Arrays.asList(
                singlePartitionContainerName,
                multiPartitionContainerName
            );
            sourceConfigMap.put("kafka.connect.cosmos.source.containers.includedList", containersIncludedList.toString());

            String singlePartitionContainerTopicName = singlePartitionContainerName + "topic";
            List<String> containerTopicMapList = Arrays.asList(singlePartitionContainerTopicName + "#" + singlePartitionContainerName);
            sourceConfigMap.put("kafka.connect.cosmos.source.containers.topicMap", containerTopicMapList.toString());

            // setup the internal state
            this.setupDefaultConnectorInternalStates(sourceConnector, sourceConfigMap);
            CosmosAsyncClient cosmosAsyncClient = KafkaCosmosReflectionUtils.getCosmosClient(sourceConnector);

            int maxTask = 2;
            List<Map<String, String>> taskConfigs = sourceConnector.taskConfigs(maxTask);
            assertThat(taskConfigs.size()).isEqualTo(maxTask);

            // construct expected feed range task units
            CosmosContainerProperties singlePartitionContainer = getSinglePartitionContainer(cosmosAsyncClient);
            List<FeedRangeTaskUnit> singlePartitionContainerFeedRangeTasks =
                getFeedRangeTaskUnits(
                    cosmosAsyncClient,
                    databaseName,
                    singlePartitionContainer,
                    null,
                    singlePartitionContainerTopicName);
            assertThat(singlePartitionContainerFeedRangeTasks.size()).isEqualTo(1);

            CosmosContainerProperties multiPartitionContainer = getMultiPartitionContainer(cosmosAsyncClient);
            List<FeedRangeTaskUnit> multiPartitionContainerFeedRangeTasks =
                getFeedRangeTaskUnits(
                    cosmosAsyncClient,
                    databaseName,
                    multiPartitionContainer,
                    null,
                    multiPartitionContainer.getId());
            assertThat(multiPartitionContainerFeedRangeTasks.size()).isGreaterThan(1);

            List<List<FeedRangeTaskUnit>> expectedTaskUnits = new ArrayList<>();
            for (int i = 0; i < maxTask; i++) {
                expectedTaskUnits.add(new ArrayList<>());
            }

            expectedTaskUnits.get(0).add(singlePartitionContainerFeedRangeTasks.get(0));
            for (int i = 0; i < multiPartitionContainerFeedRangeTasks.size(); i++) {
                int index = ( i + 1) % 2;
                expectedTaskUnits.get(index).add(multiPartitionContainerFeedRangeTasks.get(i));
            }

            validateFeedRangeTasks(expectedTaskUnits, taskConfigs);

            MetadataTaskUnit expectedMetadataTaskUnit =
                getMetadataTaskUnit(
                    cosmosAsyncClient,
                    databaseName,
                    Arrays.asList(singlePartitionContainer, multiPartitionContainer));
            validateMetadataTask(expectedMetadataTaskUnit, taskConfigs.get(1));
        } finally {
            sourceConnector.stop();
        }
    }

    @Test(groups = "{ kafka }", timeOut = TIMEOUT)
    public void getTaskConfigsAfterSplit() throws JsonProcessingException {
        // This test is to simulate after a split happen, the task resume with persisted offset
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();

        try {
            Map<String, Object> sourceConfigMap = new HashMap<>();
            sourceConfigMap.put("kafka.connect.cosmos.accountEndpoint", KafkaCosmosTestConfigurations.HOST);
            sourceConfigMap.put("kafka.connect.cosmos.accountKey", KafkaCosmosTestConfigurations.MASTER_KEY);
            sourceConfigMap.put("kafka.connect.cosmos.source.database.name", databaseName);
            List<String> containersIncludedList = Arrays.asList(multiPartitionContainerName);
            sourceConfigMap.put("kafka.connect.cosmos.source.containers.includedList", containersIncludedList.toString());

            // setup the internal state
            this.setupDefaultConnectorInternalStates(sourceConnector, sourceConfigMap);

            // override the storage reader with initial offset
            CosmosAsyncClient cosmosAsyncClient = KafkaCosmosReflectionUtils.getCosmosClient(sourceConnector);
            CosmosSourceOffsetStorageReader sourceOffsetStorageReader = KafkaCosmosReflectionUtils.getSourceOffsetStorageReader(sourceConnector);
            InMemoryStorageReader inMemoryStorageReader =
                (InMemoryStorageReader) KafkaCosmosReflectionUtils.getOffsetStorageReader(sourceOffsetStorageReader);

            CosmosContainerProperties multiPartitionContainer = getMultiPartitionContainer(cosmosAsyncClient);

            // constructing feed range continuation offset
            FeedRangeContinuationTopicPartition feedRangeContinuationTopicPartition =
                new FeedRangeContinuationTopicPartition(
                    databaseName,
                    multiPartitionContainer.getResourceId(),
                    FeedRange.forFullRange());

            String initialContinuationState = new ChangeFeedStateV1(
                multiPartitionContainer.getResourceId(),
                FeedRangeEpkImpl.forFullRange(),
                ChangeFeedMode.INCREMENTAL,
                ChangeFeedStartFromInternal.createFromBeginning(),
                FeedRangeContinuation.create(
                    multiPartitionContainer.getResourceId(),
                    FeedRangeEpkImpl.forFullRange(),
                    Arrays.asList(new CompositeContinuationToken("1", FeedRangeEpkImpl.forFullRange().getRange())))).toString();

            FeedRangeContinuationTopicOffset feedRangeContinuationTopicOffset =
                new FeedRangeContinuationTopicOffset(initialContinuationState, "1"); // using the same itemLsn as in the continuationToken
            Map<Map<String, Object>, Map<String, Object>> initialOffsetMap = new HashMap<>();
            initialOffsetMap.put(
                FeedRangeContinuationTopicPartition.toMap(feedRangeContinuationTopicPartition),
                FeedRangeContinuationTopicOffset.toMap(feedRangeContinuationTopicOffset));

            // constructing feedRange metadata offset
            FeedRangesMetadataTopicPartition feedRangesMetadataTopicPartition =
                new FeedRangesMetadataTopicPartition(databaseName, multiPartitionContainer.getResourceId());
            FeedRangesMetadataTopicOffset feedRangesMetadataTopicOffset =
                new FeedRangesMetadataTopicOffset(Arrays.asList(FeedRange.forFullRange()));
            initialOffsetMap.put(
                FeedRangesMetadataTopicPartition.toMap(feedRangesMetadataTopicPartition),
                FeedRangesMetadataTopicOffset.toMap(feedRangesMetadataTopicOffset));

            inMemoryStorageReader.populateOffset(initialOffsetMap);

            int maxTask = 2;
            List<Map<String, String>> taskConfigs = sourceConnector.taskConfigs(maxTask);
            assertThat(taskConfigs.size()).isEqualTo(maxTask);

            // construct expected feed range task units
            List<FeedRangeTaskUnit> multiPartitionContainerFeedRangeTasks =
                getFeedRangeTaskUnits(
                    cosmosAsyncClient,
                    databaseName,
                    multiPartitionContainer,
                    initialContinuationState,
                    multiPartitionContainer.getId());
            assertThat(multiPartitionContainerFeedRangeTasks.size()).isGreaterThan(1);

            List<List<FeedRangeTaskUnit>> expectedTaskUnits = new ArrayList<>();
            for (int i = 0; i < maxTask; i++) {
                expectedTaskUnits.add(new ArrayList<>());
            }

            for (int i = 0; i < multiPartitionContainerFeedRangeTasks.size(); i++) {
                expectedTaskUnits.get( i % 2).add(multiPartitionContainerFeedRangeTasks.get(i));
            }

            validateFeedRangeTasks(expectedTaskUnits, taskConfigs);

            MetadataTaskUnit expectedMetadataTaskUnit =
                getMetadataTaskUnit(
                    cosmosAsyncClient,
                    databaseName,
                    Arrays.asList(multiPartitionContainer));
            validateMetadataTask(expectedMetadataTaskUnit, taskConfigs.get(1));
        } finally {
            sourceConnector.stop();
        }
    }

    @Test(groups = "{ kafka }", timeOut = TIMEOUT)
    public void getTaskConfigsAfterMerge() throws JsonProcessingException {
        // This test is to simulate after a merge happen, the task resume with previous feedRanges
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();

        try {
            Map<String, Object> sourceConfigMap = new HashMap<>();
            sourceConfigMap.put("kafka.connect.cosmos.accountEndpoint", KafkaCosmosTestConfigurations.HOST);
            sourceConfigMap.put("kafka.connect.cosmos.accountKey", KafkaCosmosTestConfigurations.MASTER_KEY);
            sourceConfigMap.put("kafka.connect.cosmos.source.database.name", databaseName);
            List<String> containersIncludedList = Arrays.asList(singlePartitionContainerName);
            sourceConfigMap.put("kafka.connect.cosmos.source.containers.includedList", containersIncludedList.toString());

            // setup the internal state
            this.setupDefaultConnectorInternalStates(sourceConnector, sourceConfigMap);

            // override the storage reader with initial offset
            CosmosAsyncClient cosmosAsyncClient = KafkaCosmosReflectionUtils.getCosmosClient(sourceConnector);
            CosmosSourceOffsetStorageReader sourceOffsetStorageReader = KafkaCosmosReflectionUtils.getSourceOffsetStorageReader(sourceConnector);
            InMemoryStorageReader inMemoryStorageReader =
                (InMemoryStorageReader) KafkaCosmosReflectionUtils.getOffsetStorageReader(sourceOffsetStorageReader);

            CosmosContainerProperties singlePartitionContainer = getSinglePartitionContainer(cosmosAsyncClient);

            // constructing feed range continuation offset
            List<FeedRange> childRanges =
                ImplementationBridgeHelpers
                    .CosmosAsyncContainerHelper
                    .getCosmosAsyncContainerAccessor()
                    .trySplitFeedRange(
                        cosmosAsyncClient.getDatabase(databaseName).getContainer(singlePartitionContainer.getId()),
                        FeedRange.forFullRange(),
                        2)
                    .block();

            Map<Map<String, Object>, Map<String, Object>> initialOffsetMap = new HashMap<>();
            List<FeedRangeTaskUnit> singlePartitionFeedRangeTaskUnits = new ArrayList<>();

            for (FeedRange childRange : childRanges) {
                FeedRangeContinuationTopicPartition feedRangeContinuationTopicPartition =
                    new FeedRangeContinuationTopicPartition(
                        databaseName,
                        singlePartitionContainer.getResourceId(),
                        childRange);

                ChangeFeedStateV1 childRangeContinuationState = new ChangeFeedStateV1(
                    singlePartitionContainer.getResourceId(),
                    (FeedRangeEpkImpl)childRange,
                    ChangeFeedMode.INCREMENTAL,
                    ChangeFeedStartFromInternal.createFromBeginning(),
                    FeedRangeContinuation.create(
                        singlePartitionContainer.getResourceId(),
                        (FeedRangeEpkImpl)childRange,
                        Arrays.asList(new CompositeContinuationToken("1", ((FeedRangeEpkImpl)childRange).getRange()))));

                FeedRangeContinuationTopicOffset feedRangeContinuationTopicOffset =
                    new FeedRangeContinuationTopicOffset(childRangeContinuationState.toString(), "1");

                initialOffsetMap.put(
                    FeedRangeContinuationTopicPartition.toMap(feedRangeContinuationTopicPartition),
                    FeedRangeContinuationTopicOffset.toMap(feedRangeContinuationTopicOffset));

                KafkaCosmosChangeFeedState taskUnitContinuationState =
                    new KafkaCosmosChangeFeedState(childRangeContinuationState.toString(), childRange, "1");
                singlePartitionFeedRangeTaskUnits.add(
                    new FeedRangeTaskUnit(
                        databaseName,
                        singlePartitionContainer.getId(),
                        singlePartitionContainer.getResourceId(),
                        childRange,
                        taskUnitContinuationState,
                        singlePartitionContainer.getId()));
            }

            // constructing feedRange metadata offset
            FeedRangesMetadataTopicPartition feedRangesMetadataTopicPartition =
                new FeedRangesMetadataTopicPartition(databaseName, singlePartitionContainer.getResourceId());
            FeedRangesMetadataTopicOffset feedRangesMetadataTopicOffset =
                new FeedRangesMetadataTopicOffset(
                    childRanges
                        .stream()
                        .collect(Collectors.toList()));

            initialOffsetMap.put(
                FeedRangesMetadataTopicPartition.toMap(feedRangesMetadataTopicPartition),
                FeedRangesMetadataTopicOffset.toMap(feedRangesMetadataTopicOffset));

            inMemoryStorageReader.populateOffset(initialOffsetMap);

            int maxTask = 2;
            List<Map<String, String>> taskConfigs = sourceConnector.taskConfigs(maxTask);
            assertThat(taskConfigs.size()).isEqualTo(maxTask);

            // construct expected feed range task units
            assertThat(singlePartitionFeedRangeTaskUnits.size()).isEqualTo(2);

            List<List<FeedRangeTaskUnit>> expectedTaskUnits = new ArrayList<>();
            for (int i = 0; i < maxTask; i++) {
                expectedTaskUnits.add(new ArrayList<>());
            }

            for (int i = 0; i < singlePartitionFeedRangeTaskUnits.size(); i++) {
                expectedTaskUnits.get( i % 2).add(singlePartitionFeedRangeTaskUnits.get(i));
            }

            validateFeedRangeTasks(expectedTaskUnits, taskConfigs);

            Map<String, List<FeedRange>> containersEffectiveRangesMap = new HashMap<>();
            containersEffectiveRangesMap.put(
                singlePartitionContainer.getResourceId(),
                childRanges.stream().collect(Collectors.toList()));

            MetadataTaskUnit expectedMetadataTaskUnit =
                new MetadataTaskUnit(
                    databaseName,
                    Arrays.asList(singlePartitionContainer.getResourceId()),
                    containersEffectiveRangesMap,
                    "_cosmos.metadata.topic"
                );
            validateMetadataTask(expectedMetadataTaskUnit, taskConfigs.get(1));
        } finally {
            sourceConnector.stop();
        }
    }

    @Test(groups = "unit")
    public void missingRequiredConfig() {

        List<KafkaCosmosConfigEntry<?>> requiredConfigs =
            CosmosSourceConnectorTest.SourceConfigs.ALL_VALID_CONFIGS
                .stream()
                .filter(sourceConfigEntry -> !sourceConfigEntry.isOptional())
                .collect(Collectors.toList());

        assertThat(requiredConfigs.size()).isGreaterThan(1);
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        for (KafkaCosmosConfigEntry<?> configEntry : requiredConfigs) {

            Map<String, String> sourceConfigMap = this.getValidSourceConfig();
            sourceConfigMap.remove(configEntry.getName());
            Config validatedConfig = sourceConnector.validate(sourceConfigMap);
            ConfigValue configValue =
                validatedConfig
                    .configValues()
                    .stream()
                    .filter(config -> config.name().equalsIgnoreCase(configEntry.getName()))
                    .findFirst()
                    .get();

            assertThat(configValue.errorMessages()).isNotNull();
            assertThat(configValue.errorMessages().size()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test(groups = "unit")
    public void misFormattedConfig() {
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        Map<String, String> sourceConfigMap = this.getValidSourceConfig();

        String topicMapConfigName = "kafka.connect.cosmos.source.containers.topicMap";
        sourceConfigMap.put(topicMapConfigName, UUID.randomUUID().toString());

        Config validatedConfig = sourceConnector.validate(sourceConfigMap);
        ConfigValue configValue =
            validatedConfig
                .configValues()
                .stream()
                .filter(config -> config.name().equalsIgnoreCase(topicMapConfigName))
                .findFirst()
                .get();

        assertThat(configValue.errorMessages()).isNotNull();
        assertThat(
            configValue
                .errorMessages()
                .get(0)
                .contains(
                    "The topic-container map should be a comma-delimited list of Kafka topic to Cosmos containers." +
                        " Each mapping should be a pair of Kafka topic and Cosmos container separated by '#'." +
                        " For example: topic1#con1,topic2#con2."))
            .isTrue();

        // TODO[Public Preview]: add other config validations
    }

    @Test(groups = { "unit" })
    public void sourceConfigWithThroughputControl() {
        String throughputControlGroupName = "test";
        int targetThroughput= 6;
        double targetThroughputThreshold = 0.1;
        String throughputControlDatabaseName = "throughputControlDatabase";
        String throughputControlContainerName = "throughputControlContainer";

        Map<String, String> sourceConfigMap = this.getValidSourceConfig();
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.enabled", "true");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.name", throughputControlGroupName);
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughput", String.valueOf(targetThroughput));
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughputThreshold", String.valueOf(targetThroughputThreshold));
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.database", throughputControlDatabaseName);
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.container", throughputControlContainerName);

        CosmosSourceConfig sourceConfig = new CosmosSourceConfig(sourceConfigMap);
        assertThat(sourceConfig.getThroughputControlConfig()).isNotNull();
        assertThat(sourceConfig.getThroughputControlConfig().isThroughputControlEnabled()).isTrue();
        assertThat(sourceConfig.getThroughputControlConfig().getThroughputControlAccountConfig()).isNull();
        assertThat(sourceConfig.getThroughputControlConfig().getThroughputControlGroupName()).isEqualTo(throughputControlGroupName);
        assertThat(sourceConfig.getThroughputControlConfig().getTargetThroughput()).isEqualTo(targetThroughput);
        assertThat(sourceConfig.getThroughputControlConfig().getTargetThroughputThreshold()).isEqualTo(targetThroughputThreshold);
        assertThat(sourceConfig.getThroughputControlConfig().getGlobalThroughputControlDatabaseName()).isEqualTo(throughputControlDatabaseName);
        assertThat(sourceConfig.getThroughputControlConfig().getGlobalThroughputControlContainerName()).isEqualTo(throughputControlContainerName);
        assertThat(sourceConfig.getThroughputControlConfig().getGlobalThroughputControlRenewInterval()).isNull();
        assertThat(sourceConfig.getThroughputControlConfig().getGlobalThroughputControlExpireInterval()).isNull();
    }

    @Test(groups = { "unit" })
    public void invalidThroughputControlConfig() {
        CosmosSourceConnector sourceConnector = new CosmosSourceConnector();
        // invalid targetThroughput, targetThroughputThreshold, priorityLevel config and missing required config for throughput control container info

        Map<String, String> sourceConfigMap = this.getValidSourceConfig();
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.enabled", "true");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughput", "-1");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughputThreshold", "-1");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.priorityLevel", "None");

        Config config = sourceConnector.validate(sourceConfigMap);
        Map<String, List<String>> errorMessages = config.configValues().stream()
            .collect(Collectors.toMap(ConfigValue::name, ConfigValue::errorMessages));
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.name").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.targetThroughput").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.targetThroughputThreshold").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.priorityLevel").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.globalControl.database").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.globalControl.container").size()).isGreaterThan(0);

        // invalid throughput control account config with masterKey auth
        sourceConfigMap = this.getValidSourceConfig();
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.enabled", "true");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughput", "1");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.database", "ThroughputControlDatabase");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.container", "ThroughputControlContainer");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.name", "groupName");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.accountEndpoint", KafkaCosmosTestConfigurations.HOST);

        config = sourceConnector.validate(sourceConfigMap);
        errorMessages = config.configValues().stream()
            .collect(Collectors.toMap(ConfigValue::name, ConfigValue::errorMessages));
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.accountKey").size()).isGreaterThan(0);

        // targetThroughputThreshold is not supported when using add auth for throughput control
        sourceConfigMap = this.getValidSourceConfig();
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.enabled", "true");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.targetThroughputThreshold", "0.9");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.database", "ThroughputControlDatabase");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.globalControl.container", "ThroughputControlContainer");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.name", "groupName");
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.accountEndpoint", KafkaCosmosTestConfigurations.HOST);
        sourceConfigMap.put("kafka.connect.cosmos.throughputControl.auth.type", CosmosAuthTypes.SERVICE_PRINCIPAL.getName());

        config = sourceConnector.validate(sourceConfigMap);
        errorMessages = config.configValues().stream()
            .collect(Collectors.toMap(ConfigValue::name, ConfigValue::errorMessages));
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.auth.aad.clientId").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.auth.aad.clientSecret").size()).isGreaterThan(0);
        assertThat(errorMessages.get("kafka.connect.cosmos.throughputControl.account.tenantId").size()).isGreaterThan(0);
    }

    private Map<String, String> getValidSourceConfig() {
        Map<String, String> sourceConfigMap = new HashMap<>();
        sourceConfigMap.put("kafka.connect.cosmos.accountEndpoint", KafkaCosmosTestConfigurations.HOST);
        sourceConfigMap.put("kafka.connect.cosmos.accountKey", KafkaCosmosTestConfigurations.MASTER_KEY);
        sourceConfigMap.put("kafka.connect.cosmos.source.database.name", databaseName);
        List<String> containersIncludedList = Arrays.asList(singlePartitionContainerName);
        sourceConfigMap.put("kafka.connect.cosmos.source.containers.includedList", containersIncludedList.toString());

        return sourceConfigMap;
    }

    private void setupDefaultConnectorInternalStates(CosmosSourceConnector sourceConnector, Map<String, Object> sourceConfigMap) {
        CosmosSourceConfig cosmosSourceConfig = new CosmosSourceConfig(sourceConfigMap);
        KafkaCosmosReflectionUtils.setCosmosSourceConfig(sourceConnector, cosmosSourceConfig);

        CosmosAsyncClient cosmosAsyncClient = CosmosClientStore.getCosmosClient(cosmosSourceConfig.getAccountConfig());
        KafkaCosmosReflectionUtils.setCosmosClient(sourceConnector, cosmosAsyncClient);

        InMemoryStorageReader inMemoryStorageReader = new InMemoryStorageReader();
        CosmosSourceOffsetStorageReader storageReader = new CosmosSourceOffsetStorageReader(inMemoryStorageReader);
        KafkaCosmosReflectionUtils.setOffsetStorageReader(sourceConnector, storageReader);

        SourceConnectorContext connectorContext = Mockito.mock(SourceConnectorContext.class);
        MetadataMonitorThread monitorThread = new MetadataMonitorThread(
            cosmosSourceConfig.getContainersConfig(),
            cosmosSourceConfig.getMetadataConfig(),
            connectorContext,
            storageReader,
            cosmosAsyncClient);

        KafkaCosmosReflectionUtils.setMetadataMonitorThread(sourceConnector, monitorThread);
    }

    private List<FeedRangeTaskUnit> getFeedRangeTaskUnits(
        CosmosAsyncClient cosmosClient,
        String databaseName,
        CosmosContainerProperties containerProperties,
        String continuationState,
        String topicName) {

        List<FeedRange> feedRanges =
            cosmosClient
                .getDatabase(databaseName)
                .getContainer(containerProperties.getId())
                .getFeedRanges()
                .block();

        return feedRanges
            .stream()
            .map(feedRange -> {
                KafkaCosmosChangeFeedState kafkaCosmosChangeFeedState = null;
                if (StringUtils.isNotEmpty(continuationState)) {
                    ChangeFeedState changeFeedState = ChangeFeedStateV1.fromString(continuationState);
                    kafkaCosmosChangeFeedState =
                        new KafkaCosmosChangeFeedState(
                            continuationState,
                            feedRange,
                            changeFeedState.getContinuation().getCurrentContinuationToken().getToken());
                }

                return new FeedRangeTaskUnit(
                    databaseName,
                    containerProperties.getId(),
                    containerProperties.getResourceId(),
                    feedRange,
                    kafkaCosmosChangeFeedState,
                    topicName);
            })
            .collect(Collectors.toList());
    }

    private MetadataTaskUnit getMetadataTaskUnit(
        CosmosAsyncClient cosmosAsyncClient,
        String databaseName,
        List<CosmosContainerProperties> containers) {

        Map<String, List<FeedRange>> containersEffectiveRangesMap = new HashMap<>();
        for (CosmosContainerProperties containerProperties : containers) {
            List<FeedRange> feedRanges =
                cosmosAsyncClient
                    .getDatabase(databaseName)
                    .getContainer(containerProperties.getId())
                    .getFeedRanges()
                    .block();

            containersEffectiveRangesMap.put(containerProperties.getResourceId(), feedRanges);
        }

        return new MetadataTaskUnit(
            databaseName,
            containers.stream().map(CosmosContainerProperties::getResourceId).collect(Collectors.toList()),
            containersEffectiveRangesMap,
            "_cosmos.metadata.topic"
        );
    }

    private void validateFeedRangeTasks(
        List<List<FeedRangeTaskUnit>> feedRangeTaskUnits,
        List<Map<String, String>> taskConfigs) throws JsonProcessingException {

        String taskUnitsKey = "kafka.connect.cosmos.source.task.feedRangeTaskUnits";
        List<FeedRangeTaskUnit> allTaskUnitsFromTaskConfigs = new ArrayList<>();
        for (Map<String, String> taskConfig : taskConfigs) {
            List<FeedRangeTaskUnit> taskUnitsFromTaskConfig =
                Utils
                    .getSimpleObjectMapper()
                    .readValue(taskConfig.get(taskUnitsKey), new TypeReference<List<String>>() {})
                    .stream()
                    .map(taskUnitString -> {
                        try {
                            return Utils.getSimpleObjectMapper().readValue(taskUnitString, FeedRangeTaskUnit.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());
            allTaskUnitsFromTaskConfigs.addAll(taskUnitsFromTaskConfig);
        }

        Map<FeedRange, FeedRangeTaskUnit> allExpectedTaskUnits = new HashMap<>();
        feedRangeTaskUnits.forEach(taskUnits -> {
            allExpectedTaskUnits.putAll(
                taskUnits.stream().collect(Collectors.toMap(taskUnit -> taskUnit.getFeedRange(), taskUnit -> taskUnit)));
        });

        assertThat(allExpectedTaskUnits.size()).isEqualTo(allTaskUnitsFromTaskConfigs.size());
        for (FeedRangeTaskUnit feedRangeTaskUnit : allTaskUnitsFromTaskConfigs) {
            FeedRangeTaskUnit expectedTaskUnit = allExpectedTaskUnits.get(feedRangeTaskUnit.getFeedRange());
            assertThat(expectedTaskUnit).isNotNull();
            assertThat(
                Utils.getSimpleObjectMapper().writeValueAsString(expectedTaskUnit)
            ).isEqualTo(
                Utils.getSimpleObjectMapper().writeValueAsString(feedRangeTaskUnit)
            );
        }
    }

    private void validateMetadataTask(
        MetadataTaskUnit expectedMetadataTaskUnit,
        Map<String, String> taskConfig) throws JsonProcessingException {

        String taskUnitKey = "kafka.connect.cosmos.source.task.metadataTaskUnit";
        assertThat(taskConfig.containsKey(taskUnitKey));
        MetadataTaskUnit metadataTaskUnitFromTaskConfig =
            Utils.getSimpleObjectMapper().readValue(taskConfig.get(taskUnitKey), MetadataTaskUnit.class);

        assertThat(expectedMetadataTaskUnit).isEqualTo(metadataTaskUnitFromTaskConfig);
    }

    public static class SourceConfigs {
        public static final List<KafkaCosmosConfigEntry<?>> ALL_VALID_CONFIGS = Arrays.asList(
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.accountEndpoint", null, false),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.account.tenantId", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.auth.type", CosmosAuthTypes.MASTER_KEY.getName(), true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.accountKey", Strings.Emtpy, true, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.auth.aad.clientId", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.auth.aad.clientSecret", Strings.Emtpy, true, true),
            new KafkaCosmosConfigEntry<Boolean>("kafka.connect.cosmos.useGatewayMode", false, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.preferredRegionsList", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.applicationName", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.enabled", false, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.accountEndpoint", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.throughputControl.account.tenantId", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.throughputControl.auth.type", CosmosAuthTypes.MASTER_KEY.getName(), true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.accountKey", Strings.Emtpy, true, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.throughputControl.auth.aad.clientId", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.throughputControl.auth.aad.clientSecret", Strings.Emtpy, true, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.preferredRegionsList", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.useGatewayMode", false, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.name", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.targetThroughput", -1, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.targetThroughputThreshold", -1d, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.priorityLevel", "None", true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.globalControl.database", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.globalControl.container", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.globalControl.renewIntervalInMS", -1, true),
            new KafkaCosmosConfigEntry<>("kafka.connect.cosmos.throughputControl.globalControl.expireIntervalInMS", -1, true),

            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.source.database.name", null, false),
            new KafkaCosmosConfigEntry<Boolean>("kafka.connect.cosmos.source.containers.includeAll", false, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.source.containers.includedList", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.source.containers.topicMap", Strings.Emtpy, true),
            new KafkaCosmosConfigEntry<String>(
                "kafka.connect.cosmos.source.changeFeed.startFrom",
                CosmosChangeFeedStartFromModes.BEGINNING.getName(),
                true),
            new KafkaCosmosConfigEntry<String>(
                "kafka.connect.cosmos.source.changeFeed.mode",
                CosmosChangeFeedModes.LATEST_VERSION.getName(),
                true),
            new KafkaCosmosConfigEntry<Integer>("kafka.connect.cosmos.source.changeFeed.maxItemCountHint", 1000, true),
            new KafkaCosmosConfigEntry<Integer>("kafka.connect.cosmos.source.metadata.poll.delay.ms", 5 * 60 * 1000, true),
            new KafkaCosmosConfigEntry<String>(
                "kafka.connect.cosmos.source.metadata.storage.topic",
                "_cosmos.metadata.topic",
                true),
            new KafkaCosmosConfigEntry<Boolean>("kafka.connect.cosmos.source.messageKey.enabled", true, true),
            new KafkaCosmosConfigEntry<String>("kafka.connect.cosmos.source.messageKey.field", "id", true)
        );
    }
}
