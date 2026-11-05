import {
  Field,
  FieldLabel,
  FieldRequiredMark,
  FieldError,
  Input,
  PasswordInput,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";
import { normalizeDataSourceType } from "../constants";
import { buildJdbcPreview, type MySqlDriverId } from "./model";
import type { useDataSourceForm } from "./use-data-source-form";
import { AdvancedProperties } from "./advanced-properties";

export function DataSourceFormFields({ form }: { form: ReturnType<typeof useDataSourceForm> }) {
  const { intl, values, errors, patch, isOracle, optionRules } = form;

  const nameField = (
    <Field
      invalid={Boolean(errors.name)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-name" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.dsName" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-name"
          required
          size="small"
          variant="outlined"
          value={values.name}
          aria-invalid={Boolean(errors.name) || undefined}
          placeholder={intl.formatMessage({ id: "pages.datasource.form.dsNamePlaceholder" })}
          onChange={(event) => patch("name", event.target.value)}
        />
        <FieldError match={Boolean(errors.name)} className="mt-1">
          {errors.name}
        </FieldError>
      </div>
    </Field>
  );

  const jdbcPreviewField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        {intl.formatMessage({ id: "pages.datasource.form.jdbcPreview" })}
      </span>
      <div className="min-h-7 break-all py-1.5 text-xs text-[var(--yak-components-muted-text)]">
        {buildJdbcPreview(values)}
      </div>
    </div>
  );

  const oracleJdbcUrlField = (
    <Field
      invalid={Boolean(errors.jdbcUrl)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-jdbc-url" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.jdbcUrl" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-jdbc-url"
          required
          size="small"
          variant="outlined"
          value={values.jdbcUrl}
          aria-invalid={Boolean(errors.jdbcUrl) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.oracleJdbcUrlPlaceholder",
          })}
          onChange={(event) => patch("jdbcUrl", event.target.value)}
        />
        <FieldError match={Boolean(errors.jdbcUrl)} className="mt-1">
          {errors.jdbcUrl}
        </FieldError>
      </div>
    </Field>
  );

  const connectionAddressField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        <FieldRequiredMark />
        {intl.formatMessage({ id: "pages.datasource.form.connectionAddress" })}
      </span>
      <div className="grid min-w-0 grid-cols-[minmax(0,1fr)_160px] gap-2 max-sm:grid-cols-1">
        <Field invalid={Boolean(errors.host)} className="!gap-0">
          <Input
            id="datasource-host"
            required
            size="small"
            variant="outlined"
            value={values.host}
            aria-label={intl.formatMessage({ id: "pages.datasource.form.hostPlaceholder" })}
            aria-invalid={Boolean(errors.host) || undefined}
            placeholder={intl.formatMessage({ id: "pages.datasource.form.hostPlaceholder" })}
            onChange={(event) => patch("host", event.target.value)}
          />
          <FieldError match={Boolean(errors.host)} className="mt-1">
            {errors.host}
          </FieldError>
        </Field>
        <Field invalid={Boolean(errors.port)} className="!gap-0">
          <Input
            id="datasource-port"
            required
            size="small"
            variant="outlined"
            inputMode="numeric"
            value={values.port}
            aria-label={intl.formatMessage({ id: "pages.datasource.form.portPlaceholder" })}
            aria-invalid={Boolean(errors.port) || undefined}
            placeholder={intl.formatMessage({ id: "pages.datasource.form.portPlaceholder" })}
            onChange={(event) => patch("port", event.target.value)}
          />
          <FieldError match={Boolean(errors.port)} className="mt-1">
            {errors.port}
          </FieldError>
        </Field>
      </div>
    </div>
  );

  const databaseField = (
    <Field
      invalid={Boolean(errors.database)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-database" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.database" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-database"
          required
          size="small"
          variant="outlined"
          value={values.database}
          aria-invalid={Boolean(errors.database) || undefined}
          placeholder={intl.formatMessage({ id: "pages.datasource.form.databasePlaceholder" })}
          onChange={(event) => patch("database", event.target.value)}
        />
        <FieldError match={Boolean(errors.database)} className="mt-1">
          {errors.database}
        </FieldError>
      </div>
    </Field>
  );

  const accessIdentityField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        {intl.formatMessage({ id: "pages.datasource.form.accessIdentity" })}
      </span>
      <Select
        size="small"
        items={{
          USERNAME_PASSWORD: intl.formatMessage({ id: "pages.datasource.form.usernamePassword" }),
        }}
        value="USERNAME_PASSWORD"
      >
        <SelectTrigger variant="outlined">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="USERNAME_PASSWORD">
            <SelectItemText>
              {intl.formatMessage({ id: "pages.datasource.form.usernamePassword" })}
            </SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        </SelectContent>
      </Select>
    </div>
  );

  const usernameField = (
    <Field
      invalid={Boolean(errors.username)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-username" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.username" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-username"
          required
          size="small"
          variant="outlined"
          value={values.username}
          aria-invalid={Boolean(errors.username) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.usernamePlaceholder",
          })}
          onChange={(event) => patch("username", event.target.value)}
        />
        <FieldError match={Boolean(errors.username)} className="mt-1">
          {errors.username}
        </FieldError>
      </div>
    </Field>
  );

  const passwordField = (
    <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
      <FieldLabel htmlFor="datasource-password" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.password" })}
      </FieldLabel>
      <PasswordInput
        id="datasource-password"
        size="small"
        variant="outlined"
        value={values.password}
        placeholder={intl.formatMessage({
          id: "pages.datasource.form.passwordPlaceholder",
        })}
        onChange={(event) => patch("password", event.target.value)}
      />
    </Field>
  );

  const authOptionField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        {intl.formatMessage({ id: "pages.datasource.form.authOption" })}
      </span>
      <label className="flex min-h-7 items-center gap-2 text-xs text-[var(--yak-components-field-label)]">
        <input
          type="radio"
          checked
          readOnly
          className="size-3.5 accent-[var(--yak-color-primary)]"
        />
        {intl.formatMessage({ id: "pages.datasource.form.noAuth" })}
      </label>
    </div>
  );

  // The Java MySQL plugin selects its installed JDBC driver; unavailable choices are hidden.
  const mysqlDriverItems: Record<MySqlDriverId, string> = {
    AUTO: intl.formatMessage({ id: "pages.datasource.form.versionAuto" }),
    MYSQL_8: intl.formatMessage({ id: "pages.datasource.form.versionMysql8" }),
    MYSQL_5: intl.formatMessage({ id: "pages.datasource.form.versionMysql5" }),
  };

  const serverDriverIds = optionRules?.options.find(
    (option) => option.key === "driverId",
  )?.allowedValues;
  const availableDriverIds = (serverDriverIds ?? ["AUTO"]).filter(
    (driverId): driverId is MySqlDriverId =>
      driverId === "AUTO" || driverId === "MYSQL_8" || driverId === "MYSQL_5",
  );

  const versionField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        {intl.formatMessage({ id: "pages.datasource.form.version" })}
      </span>
      <Select<MySqlDriverId>
        size="small"
        items={mysqlDriverItems}
        value={values.driverId}
        onValueChange={(driverId) => {
          if (driverId) patch("driverId", driverId);
        }}
      >
        <SelectTrigger variant="outlined">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {availableDriverIds.map((driverId) => (
            <SelectItem key={driverId} value={driverId}>
              <SelectItemText>{mysqlDriverItems[driverId]}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );

  const connectionFields = isOracle ? (
    <>
      {oracleJdbcUrlField}
      {accessIdentityField}
      {usernameField}
      {passwordField}
      {authOptionField}
      <AdvancedProperties form={form} />
    </>
  ) : (
    <>
      {jdbcPreviewField}
      {connectionAddressField}
      {databaseField}
      {accessIdentityField}
      {usernameField}
      {passwordField}
      {authOptionField}
      {normalizeDataSourceType(values.dbType) === "MYSQL" ? versionField : null}
      <AdvancedProperties form={form} />
    </>
  );

  const remarkField = (
    <Field
      invalid={Boolean(errors.remark)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel htmlFor="datasource-remark" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.description" })}
      </FieldLabel>
      <div className="min-w-0">
        <Textarea
          id="datasource-remark"
          size="small"
          rows={2}
          maxLength={500}
          value={values.remark}
          className="min-h-[56px] resize-none"
          aria-invalid={Boolean(errors.remark) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.descriptionPlaceholder",
          })}
          onValueChange={(value) => patch("remark", value)}
        />
        <FieldError match={Boolean(errors.remark)} className="mt-1">
          {errors.remark}
        </FieldError>
      </div>
    </Field>
  );

  return (
    <div className="space-y-3.5">
      <section className="overflow-hidden rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)]">
        <h3 className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-3 py-2 text-xs font-medium text-[var(--yak-components-field-label)]">
          {intl.formatMessage({ id: "pages.datasource.wizard.basicInfo" })}
        </h3>
        <div className="space-y-2.5 px-3 py-3">
          {nameField}
          {remarkField}
        </div>
      </section>

      <section>
        <h3 className="mb-2 text-xs font-medium text-[var(--yak-components-field-label)]">
          {intl.formatMessage({ id: "pages.datasource.wizard.connectionConfig" })}
        </h3>
        <div className="space-y-2.5 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] px-3 py-3">
          {connectionFields}
        </div>
      </section>
    </div>
  );
}
