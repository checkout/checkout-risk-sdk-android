#  Risk Android package
[![](https://jitpack.io/v/checkout/checkout-risk-sdk-android.svg)](https://jitpack.io/#checkout/checkout-risk-sdk-android)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

The package helps collect device data for merchants with direct integration (standalone) with the package and those using [Checkout's Frames Android package](https://github.com/checkout/frames-android).

## Table of contents
- [Risk Android package](#risk-android-package)
  - [Table of contents](#table-of-contents)
  - [Requirements](#requirements)
  - [Documentation](#documentation)
    - [Usage guide](#usage-guide)
    - [Public API](#public-api)
    - [Additional Resources](#additional-resources)
  - [Demo projects](#demo-projects)
  - [Contributing](#contributing)
  - [License](#license)


## Requirements
- Android minimum SDK 21

> Compatibility verified with `targetSdk` versions 21 to 33

## Documentation
### Usage guide
  1. Add JitPack repository to the project level build.gradle file:
        ```
        // project gradle file
        allprojects {
            repositories {
                maven { url 'https://jitpack.io' }
                maven { url = uri("https://maven.fpregistry.io/releases") }
            }
        }
        ```
  2. Add Risk SDK dependency to the module gradle file:
        ```
        // module gradle file
        dependencies {
            implementation 'com.github.checkout:checkout-risk-sdk-android:<latest_version>'
        }
        ```

        > You can find more about the installation and available versions on [![](https://jitpack.io/v/checkout/checkout-risk-sdk-android.svg)](https://jitpack.io/#checkout/checkout-risk-sdk-android)

        > Please keep in mind that the Jitpack repository should to be added to the project gradle file while the dependency should be added in the module gradle file. (More about build configuration files is available [here](https://developer.android.com/studio/build)).

  3. Obtain a public API key from [Checkout Dashboard](https://dashboard.checkout.com/developers/keys).
  4. Initialise the package with the `getInstance` method passing in the required configuration (public API key and environment) early-on.
        ```kotlin
        // Example usage of package
        val yourConfig = RiskConfig(publicKey = "pk_qa_xxx", environment = RiskEnvironment.QA)
        // Initialise the package with the getInstance method early-on
        val riskInstance =
            Risk.getInstance(
                context,
                RiskConfig(
                    BuildConfig.SAMPLE_MERCHANT_PUBLIC_KEY,
                    RiskEnvironment.QA,
                    false,
                ),
            ).let {
                it?.let {
                    it
                } ?: run {
                    null
                }
            }

        ```
   5. When the shopper selects Pay, publish the device data with the `publishData` method on the Risk instance and retrieve the `deviceSessionId`.
        ```kotlin
        // Publish the device data with the publishData method
        riskInstance?.publishData().let {
            if (it is PublishDataResult.Success) {
                println("Device session ID: ${it.deviceSessionId}") // dsid_XXXX
            }
        }
        ```

### Public API
The package exposes two methods:
1. `getInstance` - This method returns a singleton instance of Risk. When the method is called, preliminary checks are made to Checkout's internal API(s) that retrieve the public keys used to initialise the package for collecting device data. If the checks fail or the merchant is disabled, `null` will be returned, else, if the checks are successful, the `Risk` instance is returned to the consumer of the package which can now be used to publish the data with the `publishData` method.

    <details>
    <summary>Arguments</summary>

    ```kotlin
    data class RiskConfig(val publicKey: String, val environment: RiskEnvironment, val framesMode: Boolean = false)

    // Instance creation of Risk Android package
    public class Risk private constructor(...) {
        public companion object {
            ...
            public suspend fun getInstance(applicaitonContext: Context, config: RiskConfig): Risk? {
                ...
            }
        }
    }

    enum class RiskEnvironment {
        QA,
        SANDBOX,
        PROD
    }
    ```
    </details>

    <details>
    <summary>Responses</summary>

    ```kotlin
    class Risk private constructor(...) {
        companion object {
            ...
            suspend fun publishData(...): ... {
                ...
            }
        }
    }
    ```
    </details>


2. `publishData` - This is used to publish and persist the device data.

    <details>
    <summary>Arguments</summary>

    ```kotlin
    public suspend fun publishData(cardToken: string? = null): PublishDataResult {
    ...
    }
    ```
    </details>

    <details>
    <summary>Responses</summary>

    ```kotlin
        public sealed class PublishDataResult {
            public data class Success(val deviceSessionId: String) : PublishDataResult()

            public data object PublishFailure : PublishDataResult()
        }
    ```
    </details>

### Additional Resources
<!-- TODO: Add website documentation link here - [Risk Android SDK documentation](https://docs.checkout.com/risk/overview) -->
- [Frames Android SDK documentation](https://www.checkout.com/docs/developer-resources/sdks/frames-android-sdk)

## Demo projects
Our sample application showcases our prebuilt UIs and how our SDK works. You can run this locally e.g. with Android Studio after adding your public key as an environment variable. See steps below:
- Add environment variable: For example if your public key is `pk_test_123` you would add the following to your `~/.bash_profile` or `~/.zshrc` file:
`export SAMPLE_MERCHANT_PUBLIC_KEY=pk_test_123`.
- Once you clone the repository, open it in Android Studio and click on the Run button.

## Contributing
Find our guide to start contributing [here](https://github.com/checkout/checkout-risk-sdk-android/blob/main/CONTRIBUTING.md).

## License
Risk Android is released under the MIT license. [See LICENSE](https://github.com/checkout/checkout-risk-sdk-android/blob/main/LICENSE) for details.
