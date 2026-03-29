import type React from 'react';

export interface ActivityItem {
  date: string;
  source: string;
  sourceIcon: React.ReactNode;
  title: string;
  detail?: string;
}

export interface ShowcaseScenario {
  key: string;
  entityName: string;
  entityIcon: React.ReactNode;
  entityColor: string;

  // Table
  tableTitle: string;
  tableSubtitle: string;
  searchPlaceholder: string;
  tableHeaders: { icon: React.ReactNode; label: string }[];
  tableRows: { cells: React.ReactNode[] }[];
  tableColTemplate: string;

  // Activity Timeline
  timelineTitle: string;
  timelineBreadcrumb: string[];
  activities: ActivityItem[];

  // Knowledge Base
  kbQuery: React.ReactNode;
  kbRetrieved: string[];
  kbAnalysedTitle: React.ReactNode;
  kbAnalysedCards: { icon: React.ReactNode; title: string; detail: string }[];
  kbIdentified: React.ReactNode;
  kbResponse: React.ReactNode;
}
