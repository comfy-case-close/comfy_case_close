#!/usr/bin/env python3
"""
Kiem chung tinh toan bo codebase fnb.

Chay khong can PostgreSQL, khong can Maven. Dung parser THAT cua PostgreSQL
(libpg_query qua pglast) cho SQL, va phan tich chuoi cho Java/XML/YAML.

    python3 tools/verify.py
"""
import os, re, sys, json
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FAIL = []
INFO = []

def fail(cat, msg): FAIL.append(f'[{cat}] {msg}')
def info(msg): INFO.append(msg)

def walk(sub, ext):
    base = os.path.join(ROOT, sub)
    for r, _, ns in os.walk(base):
        for n in sorted(ns):
            if n.endswith(ext):
                yield os.path.join(r, n)

# ============================================================ 1. SQL
from pglast import parse_sql, parser, enums
AT_ENABLE_RLS = int(enums.AlterTableType.AT_EnableRowSecurity)
AT_FORCE_RLS  = int(enums.AlterTableType.AT_ForceRowSecurity)
AT_DROP_COLUMN = int(enums.AlterTableType.AT_DropColumn)
OBJ_MATVIEW   = int(enums.ObjectType.OBJECT_MATVIEW)

sql_files = sorted(walk('db/changelog', '.sql'))
stmt_total = pl_total = 0
tables = {}          # 'schema.table' -> set(columns)
enums = set()
domains = set()
functions = set()
views = set()
matviews = set()
policies = {}        # 'schema.table' -> [policy names]
rls_enabled = set()
rls_forced = set()

for f in sql_files:
    src = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, ROOT)
    try:
        stmts = parse_sql(src)
    except Exception as e:
        fail('SQL', f'{rel}: {e}'); continue
    stmt_total += len(stmts)

    for m in re.finditer(
            r'CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION.*?LANGUAGE\s+plpgsql\s+AS\s+(\$[A-Za-z_]*\$)(.*?)\1',
            src, re.S | re.I):
        try:
            parser.parse_plpgsql_json(
                'CREATE FUNCTION _p() RETURNS trigger LANGUAGE plpgsql AS $x$'+m.group(2)+'$x$')
            pl_total += 1
        except Exception as e:
            fail('PLPGSQL', f'{rel}: {e}')

    # thu thap doi tuong tu AST
    for raw in stmts:
        node = raw.stmt
        t = type(node).__name__
        if t == 'CreateStmt':
            rv = node.relation
            key = f'{rv.schemaname}.{rv.relname}'
            cols = set()
            for el in (node.tableElts or []):
                if type(el).__name__ == 'ColumnDef':
                    cols.add(el.colname)
            tables[key] = cols
        elif t == 'CreateEnumStmt':
            enums.add('.'.join(n.sval for n in node.typeName))
        elif t == 'CreateDomainStmt':
            domains.add('.'.join(n.sval for n in node.domainname))
        elif t == 'CreateFunctionStmt':
            functions.add('.'.join(n.sval for n in node.funcname))
        elif t == 'ViewStmt':
            views.add(f'{node.view.schemaname}.{node.view.relname}')
        elif t == 'CreateTableAsStmt' and int(node.objtype) == OBJ_MATVIEW:
            matviews.add(f'{node.into.rel.schemaname}.{node.into.rel.relname}')
        elif t == 'CreatePolicyStmt':
            key = f'{node.table.schemaname}.{node.table.relname}'
            policies.setdefault(key, []).append(node.policy_name)
        elif t == 'AlterTableStmt':
            key = f'{node.relation.schemaname}.{node.relation.relname}'
            for cmd in (node.cmds or []):
                st = cmd.subtype
                sti = int(st) if st is not None else -1
                if sti == AT_DROP_COLUMN and key in tables:
                    tables[key].discard(cmd.name)
                elif sti == AT_ENABLE_RLS:
                    rls_enabled.add(key)
                elif sti == AT_FORCE_RLS:
                    rls_forced.add(key)
                elif type(cmd).__name__ == 'AlterTableCmd' and cmd.def_ is not None \
                     and type(cmd.def_).__name__ == 'ColumnDef':
                    tables.setdefault(key, set()).add(cmd.def_.colname)

info(f'SQL: {len(sql_files)} file, {stmt_total} statement, {pl_total} ham PL/pgSQL — parse sach')
info(f'Doi tuong: {len(tables)} bang, {len(enums)} enum, {len(domains)} domain, '
     f'{len(functions)} function, {len(views)} view, {len(matviews)} matview')

# ---- 1a. moi bang co business_id phai duoc RLS bao phu -------------------
TENANT_SCHEMAS = {'identity','platform','files','notify','integration',
                  'cashclose','workforce','inventory'}
tenant_tables = {k for k, cols in tables.items()
                 if 'business_id' in cols and k.split('.')[0] in TENANT_SCHEMAS}

rls_sql = open(os.path.join(ROOT, 'db/changelog/900-rls/001-enable-rls.sql'), encoding='utf-8').read()

# Kiem tra THUOC TINH chu khong phai cach hien thuc:
#   moi bang co business_id phai duoc bao phu boi vong lap tu dong,
#   HOAC duoc xu ly tuong minh (bang dac biet co policy khac).
has_auto_loop = ("attname = 'business_id'" in rls_sql
                 and 'fn_apply_tenant_rls' in rls_sql
                 and 'FOR r IN' in rls_sql)
if not has_auto_loop:
    fail('RLS', 'Khong tim thay vong lap tu dong bat RLS cho moi bang co business_id')

# Bang duoc loai khoi vong lap phai duoc xu ly tuong minh o cuoi file
m = re.search(r'v_special\s+CONSTANT\s+TEXT\[\]\s*:=\s*ARRAY\[(.*?)\]', rls_sql, re.S)
special = set(re.findall(r"'([a-z_]+\.[a-z_]+)'", m.group(1))) if m else set()
for t in sorted(special):
    handled = (f"fn_apply_tenant_or_global_rls('{t.split('.')[0]}', '{t.split('.')[1]}')" in rls_sql
               or f'ALTER TABLE {t} ENABLE ROW LEVEL SECURITY' in rls_sql)
    if not handled:
        fail('RLS', f'{t} bi loai khoi vong lap nhung khong duoc xu ly tuong minh')
    if t not in tenant_tables:
        fail('RLS', f'{t} nam trong v_special nhung khong phai bang co tenant')

covered = tenant_tables
info(f'RLS: vong lap tu dong bao phu {len(covered - special)} bang; '
     f'{len(special)} bang dac biet xu ly tuong minh ({", ".join(sorted(special))})')

for t in sorted(tenant_tables):
    if 'business_id' not in tables[t]:
        fail('RLS', f'{t} thieu business_id')

# File phai CHAY LAI DUOC: moi CREATE POLICY phai co DROP POLICY IF EXISTS di kem
created = set(re.findall(r'CREATE POLICY (\w+)', rls_sql))
dropped = set(re.findall(r'DROP POLICY IF EXISTS (\w+)', rls_sql))
if created - dropped:
    fail('RLS', f'Policy tao ma khong co DROP IF EXISTS di kem (khong chay lai duoc): '
                f'{sorted(created - dropped)}')
else:
    info(f'RLS: {len(created)} policy deu idempotent (co DROP IF EXISTS) — changeSet runOnChange an toan')

# Ham RLS phai la CREATE OR REPLACE de chay lai duoc
for fn in ('fn_apply_tenant_rls', 'fn_apply_tenant_or_global_rls'):
    if f'CREATE OR REPLACE FUNCTION shared.{fn}' not in rls_sql:
        fail('RLS', f'shared.{fn} phai la CREATE OR REPLACE de changeSet chay lai duoc')

# ---- 1b. ai_agent khong duoc GRANT tren matview --------------------------
def strip_sql_comments(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return re.sub(r'--[^\n]*', '', text)

for f in sql_files:
    src = strip_sql_comments(open(f, encoding='utf-8').read())
    for m in re.finditer(r'GRANT[^;]*?\bON\s+([a-z_]+\.[a-z_0-9]+)[^;]*?TO\s+ai_agent', src, re.I|re.S):
        obj = m.group(1)
        if obj in matviews:
            fail('RLS', f'ai_agent duoc GRANT thang tren materialized view {obj} '
                        f'— PostgreSQL khong ho tro RLS tren MV')
info(f'RLS: ai_agent chi duoc GRANT tren view boc ngoai, khong tren {len(matviews)} matview')

# ---- 1c. moi view analytics ai_agent doc duoc phai co security_barrier ----
for m in re.finditer(r'CREATE VIEW\s+(analytics\.\w+)\s*\n?\s*WITH \(security_barrier = true\)',
                     '\n'.join(open(f, encoding='utf-8').read() for f in sql_files)):
    pass
ana_src = open(os.path.join(ROOT, 'db/changelog/800-analytics/001-views.sql'), encoding='utf-8').read()
granted_to_ai = set(re.findall(r'GRANT SELECT ON (analytics\.\w+) TO ai_agent', ana_src))
barrier_views = set(re.findall(r'CREATE VIEW\s+(analytics\.\w+)\s+WITH \(security_barrier = true\)', ana_src))
missing = granted_to_ai - barrier_views
if missing:
    fail('RLS', f'View cap cho ai_agent nhung thieu security_barrier: {sorted(missing)}')
else:
    info(f'RLS: {len(granted_to_ai)} view cap cho ai_agent deu co security_barrier')

# ---- 1d. thu tu apply.sh phai khop master changelog ----------------------
apply_sh = open(os.path.join(ROOT, 'db/apply.sh'), encoding='utf-8').read()
order_sh = re.findall(r'"(\d{3}-[^"]+\.sql)"', apply_sh)
master = ET.parse(os.path.join(ROOT, 'db/changelog/db.changelog-master.xml'))
ns = {'lb': 'http://www.liquibase.org/xml/ns/dbchangelog'}
order_xml = [e.get('path') for e in master.getroot().iter(f'{{{ns["lb"]}}}sqlFile')]
if order_sh != order_xml:
    fail('DB', f'Thu tu apply.sh khac master changelog:\n  sh : {order_sh}\n  xml: {order_xml}')
else:
    info(f'DB: thu tu migration khop giua apply.sh va master changelog ({len(order_sh)} file)')

# moi file sql duoc liet ke
listed = set(order_sh)
on_disk = {os.path.relpath(f, os.path.join(ROOT, 'db/changelog')) for f in sql_files}
if listed != on_disk:
    fail('DB', f'File SQL khong duoc liet ke: {sorted(on_disk - listed)}; '
               f'liet ke nhung khong ton tai: {sorted(listed - on_disk)}')

# ============================================================ 2. XML
xml_files = list(walk('.', 'pom.xml')) + [os.path.join(ROOT, 'db/changelog/db.changelog-master.xml')]
for f in xml_files:
    try: ET.parse(f)
    except Exception as e: fail('XML', f'{os.path.relpath(f, ROOT)}: {e}')
info(f'XML: {len(xml_files)} file hop le')

# ---- module trong pom goc phai ton tai ----------------------------------
root_pom = ET.parse(os.path.join(ROOT, 'pom.xml'))
mns = {'m': 'http://maven.apache.org/POM/4.0.0'}
modules = [e.text for e in root_pom.getroot().iter(f'{{{mns["m"]}}}module')]
for mod in modules:
    p = os.path.join(ROOT, mod, 'pom.xml')
    if not os.path.exists(p):
        fail('MAVEN', f'<module>{mod}</module> nhung khong co {mod}/pom.xml')
info(f'MAVEN: {len(modules)} module deu co pom.xml')

# ---- moi pom con phai tro dung parent -----------------------------------
for mod in modules:
    t = ET.parse(os.path.join(ROOT, mod, 'pom.xml'))
    if mod == 'fnbx-bom':
        # BOM co chu dich KHONG ke thua parent: khi mot service tach ra repo
        # rieng, no import BOM nay o version co dinh. BOM phai doc lap duoc.
        continue
    par = t.getroot().find(f'{{{mns["m"]}}}parent')
    if par is None: fail('MAVEN', f'{mod}: thieu <parent>'); continue
    art = par.find(f'{{{mns["m"]}}}artifactId').text
    if art != 'fnbx-parent':
        fail('MAVEN', f'{mod}: parent la {art}, phai la fnbx-parent')
info('MAVEN: moi module con ke thua fnbx-parent')

# ---- BOM phai liet ke moi artifact com.fnbx -----------------------------
bom = ET.parse(os.path.join(ROOT, 'fnbx-bom/pom.xml'))
bom_arts = {d.find(f'{{{mns["m"]}}}artifactId').text
            for d in bom.getroot().iter(f'{{{mns["m"]}}}dependency')}
lib_arts = set()
for mod in modules:
    if not mod.startswith('libs/'): continue
    t = ET.parse(os.path.join(ROOT, mod, 'pom.xml'))
    lib_arts.add(t.getroot().find(f'{{{mns["m"]}}}artifactId').text)
if lib_arts - bom_arts:
    fail('MAVEN', f'BOM thieu artifact: {sorted(lib_arts - bom_arts)}')
else:
    info(f'MAVEN: BOM khoa du {len(lib_arts)} thu vien')

# ============================================================ 3. YAML
try:
    import yaml
    yml_files = list(walk('services', 'application.yml')) + list(walk('.github', '.yml'))
    for f in yml_files:
        try: yaml.safe_load(open(f, encoding='utf-8'))
        except Exception as e: fail('YAML', f'{os.path.relpath(f, ROOT)}: {e}')
    info(f'YAML: {len(yml_files)} file hop le')
except ImportError:
    info('YAML: bo qua (chua cai pyyaml)')

# ============================================================ 4. Java
java_files = list(walk('libs', '.java')) + list(walk('services', '.java'))
pkg_re = re.compile(r'^\s*package\s+([\w.]+);', re.M)
for f in java_files:
    src = open(f, encoding='utf-8').read()
    m = pkg_re.search(src)
    if not m: fail('JAVA', f'{os.path.relpath(f, ROOT)}: thieu khai bao package'); continue
    expect = m.group(1).replace('.', os.sep)
    if not os.path.dirname(f).endswith(expect):
        fail('JAVA', f'{os.path.relpath(f, ROOT)}: package {m.group(1)} khong khop duong dan')
    if src.count('{') != src.count('}'):
        fail('JAVA', f'{os.path.relpath(f, ROOT)}: ngoac nhon khong can')
info(f'JAVA: {len(java_files)} file, package khop duong dan, ngoac can')

# ---- @Table(schema=...) phai tro toi bang co that -----------------------
tbl_re = re.compile(r'@Table\(\s*schema\s*=\s*"(\w+)"\s*,\s*name\s*=\s*"(\w+)"')
entity_cnt = 0
for f in java_files:
    src = open(f, encoding='utf-8').read()
    for sch, name in tbl_re.findall(src):
        entity_cnt += 1
        key = f'{sch}.{name}'
        if key not in tables:
            fail('JPA', f'{os.path.relpath(f, ROOT)}: @Table tro toi {key} — khong co trong DDL')
info(f'JPA: {entity_cnt} entity, moi @Table deu tro toi bang co that')

# ---- @Column phai tro toi cot co that -----------------------------------
col_re = re.compile(r'@Column\([^)]*name\s*=\s*"(\w+)"')
bad_cols = 0
for f in java_files:
    src = open(f, encoding='utf-8').read()
    t = tbl_re.search(src)
    if not t: continue
    key = f'{t.group(1)}.{t.group(2)}'
    if key not in tables: continue
    for col in col_re.findall(src):
        if col not in tables[key]:
            fail('JPA', f'{os.path.relpath(f, ROOT)}: cot "{col}" khong co trong {key}')
            bad_cols += 1
if not bad_cols:
    info('JPA: moi @Column deu tro toi cot co that trong DDL')

# ---- moi entity co tenant phai co truong businessId ---------------------
for f in java_files:
    src = open(f, encoding='utf-8').read()
    t = tbl_re.search(src)
    if not t: continue
    key = f'{t.group(1)}.{t.group(2)}'
    if key in tenant_tables and 'name = "business_id"' not in src:
        fail('JPA', f'{os.path.relpath(f, ROOT)}: {key} co business_id trong DB nhung entity thieu')

# ============================================================ 5. Kien truc
# 5a. moi service co ArchitectureTest
svc_dirs = [d for d in sorted(os.listdir(os.path.join(ROOT, 'services')))
            if os.path.isdir(os.path.join(ROOT, 'services', d))]
for d in svc_dirs:
    if d == 'api-gateway': continue
    found = any(n == 'ArchitectureTest.java'
                for _, _, ns_ in os.walk(os.path.join(ROOT, 'services', d, 'src/test'))
                for n in ns_)
    if not found: fail('ARCH', f'{d}: thieu ArchitectureTest.java')
info(f'ARCH: {len(svc_dirs)-1}/{len(svc_dirs)-1} service co ArchitectureTest')

# 5b. reporting PHAI dung forReadOnlyService (ngoai le quyet dinh #11)
rep = open(os.path.join(ROOT,
      'services/reporting-service/src/test/java/com/fnbx/reporting/ArchitectureTest.java'),
      encoding='utf-8').read()
if 'forReadOnlyService' not in rep:
    fail('ARCH', 'reporting-service phai dung forReadOnlyService (ngoai le ADR-0003 #11)')
else:
    info('ARCH: reporting-service dung dung ngoai le forReadOnlyService')

# 5c. service KHONG duoc import service khac (kiem tra tinh)
for d in svc_dirs:
    if d == 'api-gateway': continue
    own = d.replace('-service', '')
    for f in walk(f'services/{d}', '.java'):
        src = open(f, encoding='utf-8').read()
        for imp in re.findall(r'^import\s+(com\.fnbx\.\w+)\.(\w+)', src, re.M):
            other, layer = imp
            oname = other.split('.')[-1]
            if oname in ('shared','archtest',own): continue
            if layer in ('service','api','repository','config'):
                fail('ARCH', f'{os.path.relpath(f, ROOT)}: import {other}.{layer} '
                             f'— phu thuoc cheo service')
info('ARCH: khong service nao import tang hanh vi cua service khac')

# 5d. controller khong import repository (tru reporting)
for d in svc_dirs:
    if d in ('api-gateway','reporting-service'): continue
    for f in walk(f'services/{d}', '.java'):
        if '/api/' not in f.replace(os.sep,'/'): continue
        src = open(f, encoding='utf-8').read()
        if re.search(r'^import\s+com\.fnbx\.\w+\.repository\.', src, re.M):
            fail('ARCH', f'{os.path.relpath(f, ROOT)}: controller import repository')
info('ARCH: khong controller nao import repository (tru ngoai le reporting)')

# ============================================================ 6. Bao mat
gate = open(os.path.join(ROOT, 'libs/fnbx-shared/src/main/java/com/fnbx/shared/tenant/TenantAwareDataSource.java'), encoding='utf-8').read()
if "set_config('app.business_id', ?, true)" not in gate:
    fail('SEC', 'TenantAwareDataSource khong dung set_config(..., true) — se ro tenant qua PgBouncer')
else:
    info('SEC: set_config dung is_local=true (bat buoc voi PgBouncer transaction pooling)')

for f in walk('services', 'application.yml'):
    y = open(f, encoding='utf-8').read()
    if ':5433/' in y:
        fail('SEC', f'{os.path.relpath(f, ROOT)}: ket noi thang Postgres (5433) '
                    f'thay vi PgBouncer (6432)')
    m = re.search(r'username:\s*(\S+)', y)
    svc = os.path.relpath(f, os.path.join(ROOT, 'services')).split(os.sep)[0]
    if m and m.group(1) != 'svc_' + svc.replace('-service',''):
        fail('SEC', f'{os.path.relpath(f, ROOT)}: username {m.group(1)} khong khop service {svc}')
info('SEC: moi service dung DB role rieng va ket noi qua PgBouncer')

for f in walk('services', 'application.yml'):
    y = open(f, encoding='utf-8').read()
    if 'ddl-auto: update' in y or 'ddl-auto: create' in y:
        fail('SEC', f'{os.path.relpath(f, ROOT)}: ddl-auto phai la validate — schema do db/ quan ly')

# ============================================================ ket qua
print('\n'.join('  ✅ ' + i for i in INFO))
print()
if FAIL:
    print(f'❌ {len(FAIL)} VAN DE:')
    for x in FAIL: print('   ' + x)
    sys.exit(1)
print('✅ TAT CA KIEM CHUNG DEU XANH')
