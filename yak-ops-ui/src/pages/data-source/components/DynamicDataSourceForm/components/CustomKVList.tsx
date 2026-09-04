import YakButton from '@/components/YakButton';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Form, Input } from 'antd';
import type { Rule } from 'antd/es/form';

const CustomKVList = ({ intl, field }: any) => {
  const form = Form.useFormInstance();
  const maxRows = field?.maxRows ?? 50;

  const keyRules = (currentIndex: number): Rule[] => [
    {
      required: true,
      message: intl.formatMessage({
        id: 'pages.datasource.customKv.keyRequired',
      }),
    },
    {
      validator: async (_rule, value) => {
        const normalized = String(value ?? '').trim();
        if (normalized.length > 128) {
          throw new Error(
            intl.formatMessage({ id: 'pages.datasource.customKv.keyMax' }),
          );
        }
        if (!normalized) return;

        const rows = form.getFieldValue(field.key) || [];
        const duplicated = rows.some(
          (row: any, index: number) =>
            index !== currentIndex &&
            String(row?.key ?? '').trim() === normalized,
        );
        if (duplicated) {
          throw new Error(
            intl.formatMessage({ id: 'pages.datasource.customKv.keyDuplicate' }),
          );
        }
      },
    },
  ];

  const valueRules: Rule[] = [
    {
      validator: async (_rule, value) => {
        if (value !== undefined && value !== null && String(value).length > 1024) {
          throw new Error(
            intl.formatMessage({ id: 'pages.datasource.customKv.valueMax' }),
          );
        }
      },
    },
  ];

  return (
    <div className="mb-3">
      <div className="mb-2">
        <div className="text-[13px] font-medium leading-5 text-[#344054]">
          {field.label}
        </div>
        {field.placeholder && (
          <div className="mt-0.5 text-[11px] leading-4 text-[#98a2b3]">
            {field.placeholder}
          </div>
        )}
      </div>

      <Form.List name={field.key}>
        {(fields, { add, remove }) => {
          const canAdd = fields.length < maxRows;
          return (
            <div className="overflow-hidden rounded-lg border border-[#e7e9ed] bg-white">
              <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-center gap-2 border-b border-[#eef0f3] bg-[#fafbfc] px-3 py-2 text-[11px] font-medium text-[#667085]">
                <span>
                  {intl.formatMessage({ id: 'pages.datasource.customKv.key' })}
                </span>
                <span>
                  {intl.formatMessage({ id: 'pages.datasource.customKv.value' })}
                </span>
                <span />
              </div>

              {fields.length > 0 ? (
                <div className="divide-y divide-[#f0f1f3]">
                  {fields.map(({ key, name, ...restField }) => (
                    <div
                      key={key}
                      className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-start gap-2 px-3 py-2"
                    >
                      <Form.Item
                        {...restField}
                        name={[name, 'key']}
                        rules={keyRules(name)}
                        className="!mb-0"
                      >
                        <Input
                          variant="filled"
                          placeholder={intl.formatMessage({
                            id: 'pages.datasource.customKv.keyPlaceholder',
                          })}
                        />
                      </Form.Item>

                      <Form.Item
                        {...restField}
                        name={[name, 'value']}
                        rules={valueRules}
                        className="!mb-0"
                      >
                        <Input
                          variant="filled"
                          placeholder={intl.formatMessage({
                            id: 'pages.datasource.customKv.valuePlaceholder',
                          })}
                        />
                      </Form.Item>

                      <YakButton
                        type="text"
                        size="small"
                        danger
                        iconOnly
                        icon={<DeleteOutlined />}
                        aria-label={intl.formatMessage({
                          id: 'pages.datasource.customKv.deleteAria',
                        })}
                        onClick={() => remove(name)}
                      />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-5 text-center text-xs text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.datasource.customKv.empty' })}
                </div>
              )}

              <div className="flex justify-end border-t border-[#eef0f3] bg-[#fcfcfd] px-3 py-2">
                <YakButton
                  type="text"
                  size="small"
                  icon={<PlusOutlined />}
                  disabled={!canAdd}
                  onClick={() => add({ key: '', value: '' })}
                >
                  {canAdd
                    ? intl.formatMessage({ id: 'pages.datasource.customKv.add' })
                    : intl.formatMessage(
                        { id: 'pages.datasource.customKv.maxRows' },
                        { maxRows },
                      )}
                </YakButton>
              </div>
            </div>
          );
        }}
      </Form.List>
    </div>
  );
};

export default CustomKVList;
