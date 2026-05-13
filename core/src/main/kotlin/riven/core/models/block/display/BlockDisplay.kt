package cranium.core.models.block.display

import cranium.core.models.common.structure.FormStructure

/**
 * Defines the UI structure of:
 *  - How the form fields are laid out
 *          - The types of widgets used for each field
 *          - Any additional configuration for each widget (e.g., placeholder text, options for dropdowns)
 *          - Internal Validation
 *
 *  - How the block is rendered in an overview page
 *          - Which fields are displayed
 *          - The order of fields
 *          - Any additional formatting or styling options
 *          - The rendering logic for different field types
 *
 * Example JSON Structure:
 *
 * {
 *   "form":
 *      "fields":{
 *          "phone": {"type":"phone","placeholder":"+61 ..."},
 *          ...
 *          "email": {"type":"email"}
 *      },
 *      render: [
 *      ]
 */
data class BlockDisplay(
    val form: FormStructure,
    val render: BlockRenderStructure
)