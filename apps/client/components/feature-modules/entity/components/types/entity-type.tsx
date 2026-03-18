'use client';

import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@riven/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@riven/ui/tabs';
import { Form } from '@/components/ui/form';
import { DataType, SchemaType } from '@/lib/types/common';
import { type EntityType } from '@/lib/types/entity';
import { AlertCircle } from 'lucide-react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { useConfigFormState } from '../../context/configuration-provider';
import { ConfigurationForm } from '../forms/type/configuration-form';
import { EntityTypeHeader } from '../ui/entity-type-header';
import { EntityTypeSaveButton } from '../ui/entity-type-save-button';
import { EntityTypesAttributes } from './entity-type-attributes';

interface EntityTypeOverviewProps {
  entityType: EntityType;
  workspaceId: string;
}

export const EntityTypeOverview: FC<EntityTypeOverviewProps> = ({ entityType }) => {
  // Read tab query parameter from URL
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get('tab');
  const editParam = searchParams.get('edit');

  // Valid tab values
  const validTabs = ['configuration', 'attributes'];
  const defaultTab = validTabs.includes(tabParam || '') ? tabParam! : 'configuration';

  // Active tab state
  const [activeTab, setActiveTab] = useState<string>(defaultTab);

  // Sync active tab with URL parameter changes (e.g., browser back/forward)
  useEffect(() => {
    if (defaultTab !== activeTab) {
      setActiveTab(defaultTab);
    }
  }, [defaultTab]);

  // Update URL when tab changes
  const handleTabChange = (value: string) => {
    setActiveTab(value);
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', value);
    router.push(`${pathname}?${params.toString()}`);
  };

  // Get form and submit handler from store
  const { form, handleSubmit } = useConfigFormState();

  // Determine which tabs have validation errors
  const tabErrors = useMemo(() => {
    const errors = form.formState.errors;
    const configurationFields = [
      'pluralName',
      'singularName',
      'key',
      'identifierKey',
      'description',
      'type',
    ];

    const hasConfigurationErrors = configurationFields.some(
      (field) => errors[field as keyof typeof errors],
    );

    return {
      configuration: hasConfigurationErrors,
    };
  }, [form.formState.errors]);

  const identifierKeys = useMemo(() => {
    if (!entityType.schema.properties) return [];
    return Object.entries(entityType.schema.properties)
      .filter(
        ([, attr]) =>
          attr.unique &&
          attr.required &&
          attr.key !== SchemaType.Id &&
          (attr.type === DataType.String || attr.type === DataType.Number),
      )
      .map(([id, attr]) => ({
        id,
        schema: attr,
      }));
  }, [entityType.schema.properties]);

  return (
    <>
      <Form {...form}>
        <div className="space-y-6">
          {/* Header */}
          <div className="flex items-start justify-between gap-4">
            <EntityTypeHeader>
              Manage object attributes and other relevant settings
            </EntityTypeHeader>
            <div className="shrink-0 pt-1">
              <EntityTypeSaveButton onSubmit={handleSubmit} />
            </div>
          </div>

          {/* Validation Errors */}
          {form.formState.errors.root && (
            <Alert variant="destructive">
              <AlertDescription>{form.formState.errors.root.message}</AlertDescription>
            </Alert>
          )}

          {/* Tabs */}
          <Tabs value={activeTab} onValueChange={handleTabChange} className="w-full">
            <TabsList className="w-2/5 justify-start">
              <TabsTrigger value="configuration">
                <div className="flex items-center gap-2">
                  Configuration
                  {tabErrors.configuration && (
                    <AlertCircle className="mr-1 size-4 text-destructive" />
                  )}
                </div>
              </TabsTrigger>

              <TabsTrigger value="attributes">
                <div className="flex items-center gap-2">
                  Attributes
                  <Badge className="h-4 w-5 border border-border">
                    {entityType.relationships.length +
                      Object.keys(entityType.schema.items || {}).length}
                  </Badge>
                </div>
              </TabsTrigger>
            </TabsList>

            {/* Configuration Tab */}
            <TabsContent value="configuration" className="space-y-6">
              <ConfigurationForm availableIdentifiers={identifierKeys} />
            </TabsContent>

            {/* Attributes Tab */}
            <TabsContent value="attributes" className="space-y-4">
              <EntityTypesAttributes type={entityType} editDefinitionId={editParam ?? undefined} />
            </TabsContent>
          </Tabs>
        </div>
      </Form>
    </>
  );
};
