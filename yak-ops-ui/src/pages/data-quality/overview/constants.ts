export interface QualityOverviewTabDefinition {
  key: string;
  label: string;
}

export interface QualityRadarDimensionDefinition {
  key: string;
  aliases: string[];
}

export const QUALITY_RADAR_DIMENSIONS: QualityRadarDimensionDefinition[] = [
  {
    key: 'completeness',
    aliases: ['完整性'],
  },
  {
    key: 'uniqueness',
    aliases: ['唯一性'],
  },
  {
    key: 'validity',
    aliases: ['有效性'],
  },
  {
    key: 'accuracy',
    aliases: ['准确性'],
  },
  {
    key: 'timeliness',
    aliases: ['时效性', '及时性'],
  },
];
