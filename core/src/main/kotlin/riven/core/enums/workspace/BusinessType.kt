package riven.core.enums.workspace

/**
 * Business type selected during onboarding. Determines which lifecycle
 * template is installed into the workspace.
 */
enum class BusinessType(val templateKey: String) {
    DTC_ECOMMERCE("dtc-ecommerce"),
    B2C_SAAS("b2c-saas");
}
