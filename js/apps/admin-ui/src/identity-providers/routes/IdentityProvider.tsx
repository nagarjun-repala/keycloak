import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generatePath } from "react-router-dom";
import type { RouteDef } from "../../route-config";

export type IdentityProviderTab = "settings" | "mappers" | "permissions";

export type IdentityProviderParams = {
  realm: string;
  providerId: string;
  alias: string;
  tab: IdentityProviderTab;
};

const DetailSettings = lazy(() => import("../add/DetailSettings"));

export const IdentityProviderRoute: RouteDef = {
  path: "/:realm/identity-providers/:providerId/:alias/:tab",
  element: <DetailSettings />,
  breadcrumb: (t) => t("identity-providers:providerDetails"),
  access: "view-identity-providers",
};

export const toIdentityProvider = (
  params: IdentityProviderParams
): Partial<Path> => ({
  pathname: generatePath(IdentityProviderRoute.path, params),
});
