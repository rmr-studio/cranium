package cranium.core.models.common.structure

import cranium.core.enums.block.structure.BlockFormWidgetType

/**
 * Defines per-field widget configuration for a form.
 * - This structure specifies the widget type and its per-field configuration (e.g., placeholder text, options).
 * - Validation is out of scope here; enforce via schema/services or extend this model with validation rules.
 *
 * The map keys must match the provided schema keys for the source object (entity or block).
 *
 *      Example JSON Structure:
 *          "fields": {
 *              "name": {
 *                  "type": "text",
 *                  "label": "Full Name",
 *                  "description": "Please enter your full legal name as it appears on official documents.",
 *                  "placeholder": "John Doe"
 *                  },
 *              "email": {
 *                  "type": "email",
 *                  "label": "Email Address",
 *                  "tooltip": "We’ll send confirmation emails to this address.",
 *                  "placeholder": "example@domain.com"
 *                  },
 *              "phone": {
 *                  "type": "phone",
 *                  "label": "Mobile Number",
 *                  "description": "Include country code (e.g., +61 for Australia).",
 *                  "placeholder": "+61 ..."
 *                  },
 *              "newsletter": {
 *                  "type": "checkbox",
 *                  "label": "Subscribed to Newsletter",
 *                  },
 *               "preferences": {
 *                  "type": "multiselect",
 *                  "label": "Communication Preferences",
 *                  "tooltip": "Select all the ways you’d like us to contact you.",
 *                  "options": [
 *                      { "label": "Email", "value": "email" },
 *                      { "label": "SMS", "value": "sms" },
 *                      { "label": "Phone Call", "value": "call" },
 *                      { "label": "Push Notifications", "value": "push" }
 *                  ]},
 *      },
 */
data class FormStructure(
    val fields: Map<String, FormWidgetConfig>
)

/**
 * Defines how a single form field should be rendered in the UI.
 *
 * @property type        The widget type to render (e.g., text input, dropdown, checkbox).
 * @property label       The user-facing label displayed next to the field.
 * @property description Optional helper text shown below the field for guidance.
 * @property tooltip     Optional tooltip content shown on hover for extra context.
 * @property placeholder Optional placeholder text shown inside the input when empty.
 * @property options     Optional list of options (only relevant for widgets like dropdowns, radios, checkboxes).
 */
data class FormWidgetConfig(
    val type: BlockFormWidgetType,
    val label: String,
    val description: String? = null,
    val tooltip: String? = null,
    val placeholder: String? = null,
    val options: List<Option>? = null,
)

/**
 * Represents a single selectable option for choice-based widgets
 * such as dropdowns, radio buttons, or checkboxes.
 *
 * @property label The user-facing label displayed in the UI.
 * @property value The underlying value submitted when this option is selected.
 */
data class Option(
    val label: String,
    val value: String
)