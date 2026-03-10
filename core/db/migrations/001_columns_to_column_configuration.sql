-- One-time migration: Transform existing `columns` JSONB array into `column_configuration` JSONB object.
-- Run this AFTER deploying the schema change (columns -> column_configuration).
--
-- Transforms:
--   columns: [{"key": "uuid1", "type": "ATTRIBUTE", "width": 150, "visible": true}, ...]
-- Into:
--   column_configuration: {"order": ["uuid1", ...], "overrides": {"uuid2": {"width": 300}}}
--
-- Only non-default values (width != 150 or visible != true) are stored in overrides.

UPDATE entity_types
SET column_configuration = (
    SELECT jsonb_build_object(
        'order', COALESCE(
            (SELECT jsonb_agg(elem ->> 'key')
             FROM jsonb_array_elements(columns) AS elem),
            '[]'::jsonb
        ),
        'overrides', COALESCE(
            (SELECT jsonb_object_agg(
                elem ->> 'key',
                jsonb_strip_nulls(jsonb_build_object(
                    'width', CASE WHEN (elem ->> 'width')::int != 150 THEN (elem ->> 'width')::int END,
                    'visible', CASE WHEN (elem ->> 'visible')::boolean != true THEN (elem ->> 'visible')::boolean END
                ))
            )
             FROM jsonb_array_elements(columns) AS elem
             WHERE (elem ->> 'width')::int != 150 OR (elem ->> 'visible')::boolean != true),
            '{}'::jsonb
        )
    )
)
WHERE columns IS NOT NULL;

-- After verification, drop the old column:
-- ALTER TABLE entity_types DROP COLUMN columns;
