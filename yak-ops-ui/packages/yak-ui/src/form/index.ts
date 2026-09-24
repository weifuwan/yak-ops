import type { Form as BaseFormNS } from "@base-ui/react/form";
import { Form as BaseForm } from "@base-ui/react/form";

export const Form = BaseForm;
export type FormProps<FormValues extends BaseFormNS.Values = BaseFormNS.Values> =
  BaseFormNS.Props<FormValues>;
export type FormActions = BaseFormNS.Actions;
