import type {
  CurrentUser,
  CurrentUserVO,
} from "@/services/security/account";

export const toCurrentUser = (user: CurrentUserVO): CurrentUser => ({
  ...user,
  name: user.realName?.trim() || user.userName,
  userid: String(user.id),
  email: user.email ?? undefined,
  phone: user.phone ?? undefined,
  deptId: user.deptId ?? null,
});
