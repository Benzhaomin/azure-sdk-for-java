## Release History

### 4.29.0-beta.1 (Unreleased)

#### Features Added
* Spark 3.5 support: - See [PR 39395](https://github.com/Azure/azure-sdk-for-java/pull/39395).

#### Breaking Changes

#### Bugs Fixed
* Fixed an issue causing failures when using change feed in batch mode with a batch location and `ChangeFeedBatch.planInputPartitions` is called multiple times (for example because physcial query plan gets retrieved) and some changes have been made in the monitored container between those calls). - See [PR 39635](https://github.com/Azure/azure-sdk-for-java/pull/39635)

#### Other Changes
* Optimized the partitioning strategy implementation details to avoid unnecessarily high RU usage. - See [PR 39438](https://github.com/Azure/azure-sdk-for-java/pull/39438)
  
### NOTE: See CHANGELOG.md in 3.1, 3.2, 3.3 and 3.4 projects for changes prior to 4.29.0
