# Staff positions and permissions API

All paths below are prefixed with `/api/v1` and require a valid access token and an
active staff account in its business. The tenant comes from the token, never a
request parameter. `PERMISSION_VIEW` is a **branch** permission. Having it in A
does not allow viewing another staff member's permissions in B.

## Read endpoints

| Method and path | Access and response |
| --- | --- |
| `GET /permissions` | Any active staff; permission dictionary (code, scope, description). |
| `GET /positions?branchId={branchId}` | Business position catalogue with each position's permissions; requires `PERMISSION_VIEW` in the supplied branch. Omitting `branchId` requires it in every active branch. |
| `GET /positions/{positionId}?branchId={branchId}` | One position with its permissions; same viewing rules as the catalogue. |
| `GET /staff?branchId={branchId}` | Staff in that branch with positions. Any active member of that branch may read it. `includePermissions=true` also returns each position's grants and distinct branch permissions, requiring `PERMISSION_VIEW` there. |
| `GET /staff` | Business-wide staff directory. Without permissions, requires membership in every active branch; with `includePermissions=true`, requires `PERMISSION_VIEW` in every active branch. |
| `GET /staff/{staffId}?branchId={branchId}` | Staff profile and positions in the selected branch. `includePermissions=true` adds grants and distinct branch permissions. Omit `branchId` for all active branches. Same viewing rules as the directory. |
| `GET /auth/me` | Own profile, branch positions, distinct branch permissions, and effective business permissions. No `PERMISSION_VIEW` needed. |

`StaffController` defaults `includePermissions` to `false`: `branchAccess` and
its positions omit their `permissions` fields, and `businessPermissions` is
omitted. A branch filter requires a live position in that branch. When
`includePermissions=true`, the caller needs `PERMISSION_VIEW` in that branch.
Without a filter, directory and staff detail require membership in **every
active branch**, or `PERMISSION_VIEW` in every active branch when requesting
permissions. Use `branchId` to narrow the request. A branch-filtered staff
detail returns 404 when that person has no live position in the branch.
Business permissions are returned on staff detail only when the target is the
authenticated user and `includePermissions=true`. `/auth/me` continues to
return the caller's own permissions without requiring `PERMISSION_VIEW`.

When included, each staff profile's `branchAccess` lists live positions with
their live grants, including business-scoped grants. Each branch's
`permissions` contains the distinct effective branch-scoped permissions from
those positions.

The staff directories support `page` (zero based, default 0) and `size` (1–100,
default 20), returning `content`, `page`, `size`, `totalElements`, `totalPages`, and
`last`. Pagination counts staff, not assignments, so someone with two positions
appears once. Branch-filtered responses contain only the selected branch.

`GET /branches/{branchId}/staff` returns one row per staff member. Pagination
counts staff, and each row has a `positions` array containing every assignment
in that branch (`positionId`, `assignedAt`, `revokedAt`). By default, only live
assignments are included. `includeRevoked=true` includes assignment history and
remains restricted to business `STAFF_ASSIGN`. All active staff in the business
can call the default roster.

## Profile response

`AuthUserResponse` keeps its existing fields and adds `branchAccess` and
`businessPermissions`. Login and refresh use the same enriched response as `/me`.
Example of the new fields:

```json
{
  "branchIds": ["11111111-1111-1111-1111-111111111111"],
  "branchAccess": [
    {
      "branchId": "11111111-1111-1111-1111-111111111111",
      "branchCode": "TX",
      "branchName": "Comfy Tú Xương",
      "positions": [
        {"positionId": "22222222-2222-2222-2222-222222222222", "code": "CASHIER", "name": "Cashier", "permissions": ["CLOSE_READ", "CLOSE_EDIT"]},
        {"positionId": "33333333-3333-3333-3333-333333333333", "code": "SHIFT_LEADER", "name": "Shift Leader", "permissions": ["CLOSE_READ", "CLOSE_SUBMIT"]}
      ],
      "permissions": ["CLOSE_READ", "CLOSE_EDIT", "CLOSE_SUBMIT"]
    }
  ],
  "businessPermissions": []
}
```

Branch permissions use `SELECT DISTINCT` across live assignments and grants.
Inactive branches/positions and revoked or future assignments/grants confer no
branch permissions; inactive staff have no effective permissions. Business
permissions retain the existing rule: a live assignment at an inactive branch
can still confer business authority. An empty `permissions` array means the
authorized result has no effective branch permissions.

## Changing positions and grants

`PATCH /auth/me` accepts an optional `branchPositions` map, in addition to the
existing profile fields:

```json
{
  "firstName": "Ho",
  "lastName": "Viet Bach",
  "phone": null,
  "avatarUrl": null,
  "branchPositions": {
    "11111111-1111-1111-1111-111111111111": ["22222222-2222-2222-2222-222222222222"]
  }
}
```

Each supplied branch replaces the caller's position set for that branch; an empty
array revokes its assignments. Omit `branchPositions` for an ordinary profile
edit. Any non-null map, including an empty map, requires business `STAFF_ASSIGN`.
It uses the same validation, anti-escalation and last-administrator protection as
`PUT /branches/{branchId}/staff/{staffId}/positions`. All profile and assignment
changes succeed or roll back together.

All position permission writes require business `PERMISSION_GRANT`:

| Method and path | Operation |
| --- | --- |
| `POST /positions/{positionId}/permissions` | Add the codes in `{"permissions":["PERMISSION_VIEW"]}`; preserve other grants. |
| `DELETE /positions/{positionId}/permissions/{permissionCode}` | Revoke one code; preserve other grants. |
| `PUT /positions/{positionId}/permissions` | Replace the full set using the same request body. |

Writes retain grant history, use business/position locks, preserve the last
administrator, and invalidate permission caches through existing audit triggers.

## Deployment

Apply `002-identity-025-permission-view` before deploying identity with this enum.
It adds the dictionary entry and grants it to positions already holding live
`PERMISSION_GRANT`. This gives their assignees viewing access only at branches
where those positions are assigned. New owner positions include it automatically.
No wildcard or business-level bypass is used when viewing other staff's effective
branch permissions. The existing gateway already routes `/staff`, `/positions`,
`/permissions`, `/branches`, and `/auth` to identity.

Verification: `PermissionPostgresTest` exercises branch boundaries, tenant
isolation, duplicate permissions, roster pagination grain, profile authorization,
incremental grants, last-admin protection, and cache invalidation against real
PostgreSQL RLS and triggers.
