import { Form as BaseForm } from "@base-ui/react/form";

export const Form = BaseForm;
export type FormProps<FormValues extends BaseForm.Values = BaseForm.Values> =
  BaseForm.Props<FormValues>;
export type FormActions = BaseForm.Actions;
