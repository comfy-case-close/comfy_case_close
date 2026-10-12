#!/usr/bin/env python3
r"""
Reads db/changelog/master.xml the way Liquibase does (root -> sub-masters -> changelog files, in include
order) and hands out the SQL of every changeSet, so scripts that must NOT use Liquibase (disposable
psql-only test databases, the static verifier) still apply exactly the migrations, in exactly the
order, that `docker compose up` applies.

    python3 tools/changelog_sql.py list
    python3 tools/changelog_sql.py extract OUT_DIR

`extract` writes one OUT_DIR/<changeset id>.sql per changeSet (ids are unique, so a test can `\ir` a
specific migration by name) plus OUT_DIR/manifest.tsv, one line per file in application order:
    <file name> TAB tx|notx      (notx = the changeSet has runInTransaction="false")
Apply them in manifest order; wrap the "tx" ones in `psql --single-transaction` to match Liquibase.
"""
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass

NS = "{http://www.liquibase.org/xml/ns/dbchangelog}"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(ROOT, "db", "changelog", "master.xml")


@dataclass
class ChangeSet:
    id: str
    author: str
    file: str             # path relative to db/changelog, forward slashes
    in_transaction: bool
    run_on_change: bool
    sql: str


def _rel(path):
    return os.path.relpath(path, os.path.dirname(MASTER)).replace(os.sep, "/")


def walk(master=MASTER):
    """Yield (changelog file, ChangeSet or None) in the order Liquibase reads them."""
    tree = ET.parse(master).getroot()
    for node in tree:
        if node.tag == NS + "include":
            target = os.path.normpath(os.path.join(os.path.dirname(master), node.attrib["file"]))
            yield from walk(target)
        elif node.tag == NS + "changeSet":
            sql = "".join((s.text or "") for s in node.findall(NS + "sql"))
            yield _rel(master), ChangeSet(
                id=node.attrib["id"], author=node.attrib["author"], file=_rel(master),
                in_transaction=node.attrib.get("runInTransaction", "true") != "false",
                run_on_change=node.attrib.get("runOnChange", "false") == "true",
                sql=sql.strip("\n") + "\n")
    yield _rel(master), None


def changesets(master=MASTER):
    return [c for _, c in walk(master) if c is not None]


def changelog_files(master=MASTER):
    """Every changelog file reached from the master, in include order, each once."""
    seen = []
    for f, _ in walk(master):
        if f not in seen:
            seen.append(f)
    return seen


def extract(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    manifest = []
    for cs in changesets():
        name = f"{cs.id}.sql"
        with open(os.path.join(out_dir, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(cs.sql)
        manifest.append(f"{name}\t{'tx' if cs.in_transaction else 'notx'}")
    with open(os.path.join(out_dir, "manifest.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(manifest) + "\n")
    return len(manifest)


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args or args[0] == "list":
        for i, cs in enumerate(changesets(), 1):
            flags = ("" if cs.in_transaction else " runInTransaction=false") + (" runOnChange" if cs.run_on_change else "")
            print(f"{i:3d}  {cs.file}  [{cs.id}]{flags}")
    elif args[0] == "extract" and len(args) >= 2:
        print(extract(args[1]), "changesets extracted to", args[1])
    else:
        sys.exit(__doc__)
