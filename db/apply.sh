#!/usr/bin/env bash
# ============================================================================
# Trinh chay migration - LIQUIBASE THAT (khong con vong lap psql thu cong)
#
# ADR-0003 quyet dinh #7:
#     db/  --migration-->  fnbx_oltp  --roi moi-->  deploy service
#     KHONG BAO GIO nguoc lai. Service KHONG tu migrate: role svc_* khong he
#     co quyen DDL, va spring.jpa.hibernate.ddl-auto la `validate`.
#
# THU TU nam DUY NHAT trong changelog/db.changelog-master.xml.
# Truoc day script nay cho mot mang FILES=(...) CHEP LAI thu tu do - hai nguon
# su that, dong bo bang tay, va khong co gi ngan CI chay mot tap file khac voi
# may dev. Mang do da bi xoa han.
#
# Liquibase chay trong container (profile "migrate" cua docker-compose.yml),
# nen khong ai phai cai Liquibase - dung tinh than cu, nhung gio co that
# DATABASECHANGELOG, changelog lock va rollback.
#
# CACH DUNG
#   ./apply.sh                   update (mac dinh), roi chay canh gac RLS
#   ./apply.sh status            con changeset nao chua chay
#   ./apply.sh history           da chay gi, luc nao, ai la author
#   ./apply.sh validate          kiem tra changelog TRUOC khi commit
#   ./apply.sh update-sql        IN ra SQL se chay, khong dong vao DB
#   ./apply.sh rollback-count 1  lui lai 1 changeset
#   ./apply.sh drop-all          xoa sach schema (CHI khi chua co du lieu that)
#
#   ./apply.sh changelog-sync    CHI MOT LAN, tren DB da migrate bang psql truoc
#                                day: danh dau MOI changeset la "da chay" ma
#                                KHONG thuc thi SQL nao. Bo qua buoc nay thi
#                                lenh update dau tien se co CREATE TABLE de len
#                                bang da ton tai va that bai.
#
# BIEN MOI TRUONG
#   FNBX_SKIP_RLS_GUARD=1        bo qua canh gac RLS (CI chay no thanh step rieng)
# ============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE=(docker compose -f "${HERE}/docker-compose.yml")

# Khong dung `shift` roi `"$@"` rong: bash 3.2 (mac dinh tren macOS) bao loi
# unbound variable voi set -u.
CMD="${1:-update}"
ARGS=("${CMD}")
if (( $# > 1 )); then ARGS+=("${@:2}"); fi

echo "==> liquibase ${ARGS[*]}"
# --rm: container migration la dung mot lan roi bo.
# depends_on trong compose da doi postgres healthy, nen khong can sleep/retry.
"${COMPOSE[@]}" --profile migrate run --rm liquibase "${ARGS[@]}"

# Canh gac RLS chi co y nghia sau khi schema vua doi. `status`, `history`,
# `validate`, `update-sql` khong dong vao DB nen khong chay guard.
case "${CMD}" in
  update|update-count|update-to-tag|changelog-sync|rollback*|drop-all)
    if [[ "${FNBX_SKIP_RLS_GUARD:-0}" == "1" ]]; then
      echo
      echo "==> Bo qua canh gac RLS (FNBX_SKIP_RLS_GUARD=1)"
    else
      echo
      echo "==> Migration xong. Chay canh gac RLS..."
      "${HERE}/rls-guard.sh"
    fi
    ;;
esac
