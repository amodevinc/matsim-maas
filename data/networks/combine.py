#!/usr/bin/env python3
"""combine_graphml_to_matsim.py

Create a **MATSim‑compatible** network by merging a vehicle (drive) graph and
a pedestrian (walk) graph given in GraphML.  The script:

* uses only the Python standard library plus **networkx**;
* writes two outputs side‑by‑side:  `<name>.xml` and `<name>.xml.gz`;
* injects the required MATSim DTD line;
* provides sensible defaults (15 m/s for cars, 1.4 m/s for walking, great‑
  circle fallback for missing link lengths).

```
python combine_graphml_to_matsim.py drive.graphml walk.graphml network.xml
```  
(replacing *network.xml* with any name you like; the gzipped copy is written
next to it.)
"""
from __future__ import annotations

import argparse
import gzip
import math
import sys
from pathlib import Path
from typing import Dict
from pyproj import Transformer

import networkx as nx
import xml.etree.ElementTree as ET

# ----------------------------------------------------------------------
# constants & defaults
# ----------------------------------------------------------------------
CAR_DEFAULT_SPEED = 15.0      # [m/s] ≈ 54 km/h
WALK_DEFAULT_SPEED = 1.4      # [m/s]
DEFAULT_CAPACITY   = 1000.0   # [pcu/h]
DEFAULT_LANES      = 1
MATSim_DOCTYPE     = (
    b'<!DOCTYPE network SYSTEM "http://www.matsim.org/files/dtd/network_v2.dtd">\n'
)

# Transformer: WGS84 → Korea Central (EPSG:5179)
transformer = Transformer.from_crs("EPSG:4326", "EPSG:5179", always_xy=True)


# ----------------------------------------------------------------------
# helpers
# ----------------------------------------------------------------------

def haversine(lon1: float, lat1: float, lon2: float, lat2: float) -> float:
    """Return great‑circle distance in *metres* between two lon/lat points."""
    R = 6_371_000  # Earth radius [m]
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi       = phi2 - phi1
    dlambda    = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2)**2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def read_graph(path: Path) -> nx.Graph:
    try:
        return nx.read_graphml(path)
    except Exception as exc:
        sys.exit(f"Error reading {path}: {exc}")

# ----------------------------------------------------------------------
# XML builders
# ----------------------------------------------------------------------

def build_network_root() -> ET.Element:
    root = ET.Element("network")
    ET.SubElement(root, "nodes")
    ET.SubElement(root, "links")
    return root


def add_node(root: ET.Element, node_id: str, x: float, y: float) -> None:
    ET.SubElement(root.find("nodes"), "node", {"id": node_id, "x": f"{x}", "y": f"{y}"})


def add_link(
    root: ET.Element,
    link_id: str,
    from_id: str,
    to_id: str,
    length: float,
    freespeed: float,
    modes: str,
    capacity: float = DEFAULT_CAPACITY,
    permlanes: int = DEFAULT_LANES,
) -> None:
    """Insert a <link> element using MATSim attribute names."""
    attr = {
        "id":        link_id,
        "from":      from_id,   # MATSim expects exactly "from"
        "to":        to_id,
        "length":    f"{length:.3f}",
        "freespeed": f"{freespeed:.3f}",
        "capacity":  f"{capacity:.1f}",
        "permlanes": str(permlanes),
        "modes":     modes,
    }
    ET.SubElement(root.find("links"), "link", attr)

# ----------------------------------------------------------------------
# graph → MATSim conversion
# ----------------------------------------------------------------------

def graph_to_matsim(
    g: nx.Graph,
    root: ET.Element,
    node_prefix: str,
    link_prefix: str,
    default_speed: float,
    modes: str,
) -> None:
    """Append nodes & links from *g* to *root* with prefixed IDs."""
    nid_map: Dict[str, str] = {}

    # ---------------- nodes ----------------
    for nid, attrs in g.nodes(data=True):
        pid = f"{node_prefix}{nid}"
        nid_map[nid] = pid
        lon = attrs.get("x") or attrs.get("lon") or attrs.get("lng")
        lat = attrs.get("y") or attrs.get("lat")

        if lon is None or lat is None:
            raise ValueError(f"Node {nid} missing coordinate attributes in GraphML")

        x, y = transformer.transform(float(lon), float(lat))

        if x is None or y is None:
            raise ValueError(f"Node {nid} missing coordinate attributes in GraphML")
        if root.find(f"nodes/node[@id='{pid}']") is None:
            add_node(root, pid, float(x), float(y))

    # ---------------- links ---------------
    seq = 0
    for u, v, edata in g.edges(data=True):
        src, tgt = nid_map[u], nid_map[v]

        length = float(edata.get("distance") or edata.get("length") or 0.0)
        if length == 0.0:
            lon1, lat1 = float(g.nodes[u]["x"]), float(g.nodes[u]["y"])
            lon2, lat2 = float(g.nodes[v]["x"]), float(g.nodes[v]["y"])
            length = haversine(lon1, lat1, lon2, lat2)

        freespeed = float(edata.get("walking_speed") or edata.get("maxspeed") or default_speed)

        oneway_flag = edata.get("oneway")
        oneway = True if oneway_flag is None else str(oneway_flag).lower() in {"true", "1", "yes"}

        link_id = f"{link_prefix}{seq}"; seq += 1
        add_link(root, link_id, src, tgt, length, freespeed, modes)

        if not oneway:
            rev_id = f"{link_prefix}{seq}"; seq += 1
            add_link(root, rev_id, tgt, src, length, freespeed, modes)

# ----------------------------------------------------------------------
# pretty‑print helper (Py < 3.9)
# ----------------------------------------------------------------------

def _indent(elem: ET.Element, level: int = 0):
    pad = "\n" + "  " * level
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = pad + "  "
        for child in elem:
            _indent(child, level + 1)
        if not child.tail or not child.tail.strip():
            child.tail = pad
    if level and (not elem.tail or not elem.tail.strip()):
        elem.tail = pad

# ----------------------------------------------------------------------
# write XML + gz
# ----------------------------------------------------------------------

def write_outputs(root: ET.Element, target: Path) -> None:
    """Pretty‑print *root* and write both XML and gzipped XML."""
    if target.suffix == ".gz":
        xml_path = Path(str(target)[:-3])
        gz_path  = target
    else:
        xml_path = target
        gz_path  = target.with_suffix(target.suffix + ".gz")

    # pretty formatting
    tree = ET.ElementTree(root)
    try:
        ET.indent(tree, space="  ")  # Py ≥ 3.9
    except AttributeError:
        _indent(root)

    body = ET.tostring(root, encoding="utf-8", xml_declaration=False)
    xml_bytes = b'<?xml version="1.0" encoding="utf-8"?>\n' + MATSim_DOCTYPE + body

    xml_path.write_bytes(xml_bytes)
    print(f"XML  -> {xml_path}")

    with gzip.open(gz_path, "wb") as fh:
        fh.write(xml_bytes)
    print(f"GZIP -> {gz_path}")

# ----------------------------------------------------------------------
# CLI & entry point
# ----------------------------------------------------------------------

def parse_args():
    p = argparse.ArgumentParser(description="Merge drive & walk GraphML into MATSim network (XML + .gz)")
    p.add_argument("drive_graphml", type=Path, help="Vehicle network GraphML")
    p.add_argument("walk_graphml",  type=Path, help="Walking network GraphML")
    p.add_argument("output",        type=Path, help="Output file name (xml or xml.gz)")
    return p.parse_args()


def main() -> None:
    args = parse_args()

    drive_g = read_graph(args.drive_graphml)
    walk_g  = read_graph(args.walk_graphml)

    root = build_network_root()
    graph_to_matsim(drive_g, root, "v_", "car_",  CAR_DEFAULT_SPEED,  "car")
    graph_to_matsim(walk_g,  root, "p_", "walk_", WALK_DEFAULT_SPEED, "walk")

    write_outputs(root, args.output)

if __name__ == "__main__":
    main()
