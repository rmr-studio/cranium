
import { DataFormat, DataType, IconColour, IconType, SchemaOptions, Icon, SchemaType } from "@/lib/types/common";




export interface AttributeSchemaType {
    label: string;
    key: SchemaType;
    type: DataType;
    format?: DataFormat;
    options?: SchemaOptions;
    icon: Icon;
}

export const attributeTypes: Record<SchemaType, AttributeSchemaType> = {
    [SchemaType.Text]: {
        label: "Text",
        key: SchemaType.Text,
        type: DataType.String,
        icon: {
            type: IconType.ALargeSmall,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Number]: {
        label: "Number",
        key: SchemaType.Number,
        type: DataType.Number,
        icon: {
            type: IconType.Calculator,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Checkbox]: {
        label: "Checkbox",
        key: SchemaType.Checkbox,
        type: DataType.Boolean,
        icon: {
            type: IconType.CheckCheck,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Date]: {
        label: "Date",
        key: SchemaType.Date,
        type: DataType.String,
        format: DataFormat.Date,
        icon: {
            type: IconType.CalendarRange,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Datetime]: {
        label: "Date & Time",
        key: SchemaType.Datetime,
        type: DataType.String,
        format: DataFormat.Datetime,
        icon: {
            type: IconType.CalendarClock,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Rating]: {
        label: "Rating",
        key: SchemaType.Rating,
        type: DataType.Number,
        options: { minimum: 1, maximum: 5 },
        icon: {
            type: IconType.Star,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Phone]: {
        label: "Phone",
        key: SchemaType.Phone,
        type: DataType.String,
        format: DataFormat.Phone,
        icon: {
            type: IconType.Phone,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Email]: {
        label: "Email",
        key: SchemaType.Email,
        type: DataType.String,
        format: DataFormat.Email,
        icon: {
            type: IconType.AtSign,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Url]: {
        label: "URL",
        key: SchemaType.Url,
        type: DataType.String,
        format: DataFormat.Url,
        icon: {
            type: IconType.Link,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Currency]: {
        label: "Currency",
        key: SchemaType.Currency,
        type: DataType.Number,
        format: DataFormat.Currency,
        icon: {
            type: IconType.DollarSign,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Percentage]: {
        label: "Percentage",
        key: SchemaType.Percentage,
        type: DataType.Number,
        format: DataFormat.Percentage,
        icon: {
            type: IconType.Percent,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Select]: {
        label: "Select",
        key: SchemaType.Select,
        type: DataType.String,
        icon: {
            type: IconType.List,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.MultiSelect]: {
        label: "Multi-select",
        key: SchemaType.MultiSelect,
        type: DataType.Array,
        icon: {
            type: IconType.ListChecks,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.FileAttachment]: {
        label: "File Attachments",
        key: SchemaType.FileAttachment,
        type: DataType.Array,
        icon: {
            type: IconType.Paperclip,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Object]: {
        label: "JSON Data",
        key: SchemaType.Object,
        type: DataType.Object,
        icon: {
            type: IconType.Code,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Location]: {
        label: "Location",
        key: SchemaType.Location,
        type: DataType.Object,
        icon: {
            type: IconType.MapPin,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Id]: {
        label: "Record ID",
        key: SchemaType.Id,
        type: DataType.String,
        icon: {
            type: IconType.Hash,
            colour: IconColour.Neutral,
        },
    },
    [SchemaType.Note]: {
        label: "Note",
        key: SchemaType.Note,
        type: DataType.Object,
        icon: {
            type: IconType.StickyNote,
            colour: IconColour.Neutral,
        },
    },
};
