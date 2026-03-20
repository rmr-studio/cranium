package riven.core.enums.workspace

import riven.core.lifecycle.B2C_SAAS_MODELS
import riven.core.lifecycle.CoreModelSet
import riven.core.lifecycle.DTC_ECOMMERCE_MODELS

/**
 * Business type selected during onboarding. Determines which core lifecycle
 * model set is installed into the workspace.
 */
enum class BusinessType(val modelSet: CoreModelSet) {
    DTC_ECOMMERCE(DTC_ECOMMERCE_MODELS),
    B2C_SAAS(B2C_SAAS_MODELS);

    /** Manifest key for catalog lookup. Delegates to the model set. */
    val templateKey: String get() = modelSet.manifestKey
}
