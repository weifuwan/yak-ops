import type { ComponentType } from "react";

import AtomIndicatorPage, { AtomIndicatorTabContent } from "../../atom-indicator";
import DerivativeIndicatorPage, { DerivativeIndicatorTabContent } from "../../derivative-indicator";
import CompositeIndicatorPage, { CompositeIndicatorTabContent } from "../../composite-indicator";
import ModifierPage, { ModifierIndicatorTabContent } from "../../modifier";
import TimePeriodPage, { TimePeriodIndicatorTabContent } from "../../time-period";
import type { IndicatorPageProps, IndicatorKind } from "../types";
import type { IndicatorTabContentProps } from "../components/workspace/indicator-tab-content";

export type IndicatorPageRegistration = {
  Page: ComponentType<IndicatorPageProps>;
  TabContent: ComponentType<IndicatorTabContentProps>;
};

export const INDICATOR_PAGE_REGISTRY: Record<IndicatorKind, IndicatorPageRegistration> = {
  atomIndicator: { Page: AtomIndicatorPage, TabContent: AtomIndicatorTabContent },
  derivativeIndicator: { Page: DerivativeIndicatorPage, TabContent: DerivativeIndicatorTabContent },
  composeIndicator: { Page: CompositeIndicatorPage, TabContent: CompositeIndicatorTabContent },
  modifier: { Page: ModifierPage, TabContent: ModifierIndicatorTabContent },
  timePeriod: { Page: TimePeriodPage, TabContent: TimePeriodIndicatorTabContent },
};
