import { createReactInlineContentSpec } from '@blocknote/react';

export const EntityMention = createReactInlineContentSpec(
  {
    type: 'entityMention',
    propSchema: {
      entityId: { default: '' },
      entityTypeKey: { default: '' },
      label: { default: '' },
    },
    content: 'none',
  },
  {
    render: (props) => (
      <span
        className="inline-flex items-center gap-1 rounded bg-primary/10 px-1.5 py-0.5 text-sm font-medium text-primary cursor-pointer"
        data-entity-id={props.inlineContent.props.entityId}
        data-entity-type={props.inlineContent.props.entityTypeKey}
      >
        @{props.inlineContent.props.label || 'Unknown'}
      </span>
    ),
  }
);
