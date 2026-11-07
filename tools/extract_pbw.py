#!/usr/bin/env python3
"""
Pebble .pbw Watchface Extractor & Base64 Encoder
Unpacks Pebble bundle files (.pbw), reads appinfo.json, extracts all
monochrome image assets, and outputs base64-encoded data URIs ready for
embedding into offline E-Ink HTML watchfaces.
"""

import os
import sys
import json
import zipfile
import base64

def extract_pbw(pbw_path, output_dir=None):
    if not os.path.isfile(pbw_path):
        print(f"Error: File '{pbw_path}' not found.")
        sys.exit(1)

    if output_dir is None:
        base_name = os.path.splitext(os.path.basename(pbw_path))[0]
        output_dir = os.path.join(os.getcwd(), base_name + "_extracted")

    os.makedirs(output_dir, exist_ok=True)
    print(f"Extracting '{pbw_path}' -> '{output_dir}'...")

    with zipfile.ZipFile(pbw_path, 'r') as z:
        z.extractall(output_dir)

    appinfo_file = os.path.join(output_dir, "appinfo.json")
    if os.path.isfile(appinfo_file):
        with open(appinfo_file, 'r', encoding='utf-8') as f:
            appinfo = json.load(f)
        print("\n=== Pebble App Metadata ===")
        print(f"  App Name:     {appinfo.get('longName', appinfo.get('shortName', 'Unknown'))}")
        print(f"  Company:      {appinfo.get('companyName', 'Unknown')}")
        print(f"  Version:      {appinfo.get('versionLabel', '1.0')}")
        print(f"  Watchface:    {appinfo.get('watchface', False)}")
        print(f"  UUID:         {appinfo.get('uuid', 'N/A')}")
        
        media_list = appinfo.get("resources", {}).get("media", [])
        print(f"  Resources:    {len(media_list)} item(s) declared\n")

    # Locate all extracted image files and create base64 mapping
    b64_map = {}
    for root, _, files in os.walk(output_dir):
        for f in files:
            if f.lower().endswith(('.png', '.bmp', '.jpg')):
                rel_path = os.path.relpath(os.path.join(root, f), output_dir)
                full_path = os.path.join(root, f)
                with open(full_path, 'rb') as img_f:
                    data = img_f.read()
                    b64 = "data:image/png;base64," + base64.b64encode(data).decode('utf-8')
                    b64_map[rel_path] = b64

    # Output JavaScript data URI map
    b64_js_path = os.path.join(output_dir, "embedded_assets.js")
    with open(b64_js_path, 'w', encoding='utf-8') as f:
        f.write("// Auto-generated base64 asset mapping from Pebble .pbw\n")
        f.write("var EMBEDDED_ASSETS = ")
        json.dump(b64_map, f, indent=2)
        f.write(";\n")

    print(f"Successfully converted {len(b64_map)} image(s) to base64 Data URIs!")
    print(f"Generated asset map: {b64_js_path}")
    print("\nNext steps:")
    print("  1. Inspect the extracted PNGs in the output directory.")
    print("  2. Reference EMBEDDED_ASSETS['filename.png'] in your E-Ink HTML watchface.")
    print("  3. Consult docs/CONVERTING_WATCHFACES.md for legacy WebKit layout tips.\n")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 extract_pbw.py <watchface.pbw> [output_directory]")
        sys.exit(1)
    
    pbw_input = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else None
    extract_pbw(pbw_input, out_dir)
