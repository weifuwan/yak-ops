export interface DataQualityIntl {
  formatMessage: (
    descriptor: { id: string },
    values?: Record<string, string | number>,
  ) => string;
}

const DIMENSION_MESSAGE_IDS: Record<string, string> = {
  全部: 'pages.dataQuality.common.dimension.all',
  完整性: 'pages.dataQuality.common.dimension.completeness',
  唯一性: 'pages.dataQuality.common.dimension.uniqueness',
  有效性: 'pages.dataQuality.common.dimension.validity',
  准确性: 'pages.dataQuality.common.dimension.accuracy',
  一致性: 'pages.dataQuality.common.dimension.consistency',
  时效性: 'pages.dataQuality.common.dimension.timeliness',
  及时性: 'pages.dataQuality.common.dimension.timeliness',
  规范性: 'pages.dataQuality.common.dimension.standardization',
  自定义: 'pages.dataQuality.common.dimension.custom',
};

export const formatQualityDimension = (
  intl: DataQualityIntl,
  value?: string,
) => {
  if (!value) return value || '';
  const id = DIMENSION_MESSAGE_IDS[value];
  return id ? intl.formatMessage({ id }) : value;
};
