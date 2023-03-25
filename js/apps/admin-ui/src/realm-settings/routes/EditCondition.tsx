import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generatePath } from "react-router-dom";
import type { RouteDef } from "../../route-config";

export type EditClientPolicyConditionParams = {
  realm: string;
  policyName: string;
  conditionName: string;
};

const NewClientPolicyCondition = lazy(
  () => import("../NewClientPolicyCondition")
);

export const EditClientPolicyConditionRoute: RouteDef = {
  path: "/:realm/realm-settings/client-policies/:policyName/edit-policy/:conditionName/edit-condition",
  element: <NewClientPolicyCondition />,
  breadcrumb: (t) => t("realm-settings:editCondition"),
  access: "manage-clients",
};

export const toEditClientPolicyCondition = (
  params: EditClientPolicyConditionParams
): Partial<Path> => ({
  pathname: generatePath(EditClientPolicyConditionRoute.path, params),
});
