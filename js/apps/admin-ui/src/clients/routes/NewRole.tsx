import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generatePath } from "react-router-dom";
import type { RouteDef } from "../../route-config";

export type NewRoleParams = { realm: string; clientId: string };

const CreateClientRole = lazy(() => import("../roles/CreateClientRole"));

export const NewRoleRoute: RouteDef = {
  path: "/:realm/clients/:clientId/roles/new",
  element: <CreateClientRole />,
  breadcrumb: (t) => t("roles:createRole"),
  access: "manage-clients",
};

export const toCreateRole = (params: NewRoleParams): Partial<Path> => ({
  pathname: generatePath(NewRoleRoute.path, params),
});
