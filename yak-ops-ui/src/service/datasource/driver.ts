import HttpUtils from '@/utils/HttpUtils';

import type { DriverUploadResult } from './types';

const DRIVER_UPLOAD_API = '/api/v1/data-source/plugin/driver/upload';

export const uploadDataSourceDriver = async (
  pluginType: string,
  file: File,
): Promise<string> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('pluginType', pluginType);

  const data = await HttpUtils.unwrap(
    await HttpUtils.postForm<DriverUploadResult | string>(
      DRIVER_UPLOAD_API,
      formData,
      { businessErrorMode: 'reject' },
    ),
  );

  const driverLocation =
    typeof data === 'string' ? data : data?.path || data?.fileName || '';

  if (!driverLocation) {
    throw new Error('驱动包上传成功，但服务端未返回驱动位置');
  }

  return driverLocation;
};
