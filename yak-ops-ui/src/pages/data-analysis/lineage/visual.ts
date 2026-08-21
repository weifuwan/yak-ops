import type { LineageAssetType, LineageRelationType } from './types';

export interface LineageAssetVisual {
  accent: string;
  soft: string;
  softStrong: string;
  border: string;
  glow: string;
}

/**
 * Keep the canvas neutral and use color only as a small semantic cue.
 * This mirrors metadata tools such as OpenMetadata where service/entity icons
 * carry most of the color while lineage cards remain white and restrained.
 */
export const lineageAssetVisual: Record<LineageAssetType, LineageAssetVisual> = {
  TABLE: {
    accent: '#3974D5',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  COLUMN: {
    accent: '#2F7F89',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  SQL_TASK: {
    accent: '#7057B8',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  DATASET: {
    accent: '#D84A63',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  DATASET_FIELD: {
    accent: '#9B5F87',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  CHART: {
    accent: '#B96A2A',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
  DASHBOARD: {
    accent: '#3C8665',
    soft: '#F8F9FB',
    softStrong: '#F2F4F7',
    border: '#D0D5DD',
    glow: 'transparent',
  },
};

// Relations deliberately share one neutral blue-gray so the graph does not
// turn into a rainbow. Selection is emphasized separately in the page.
export const lineageRelationColor: Record<LineageRelationType, string> = {
  READS_FROM: '#8BA2BC',
  WRITES_TO: '#8BA2BC',
  DERIVES_FROM: '#8BA2BC',
  CONSUMES: '#8BA2BC',
  CONTAINS: '#8BA2BC',
};
