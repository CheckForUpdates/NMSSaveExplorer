import json

with open("mapping.json", "r", encoding="utf-8") as f:
    data = json.load(f)

# Extract only the Mapping array
flat = {}
for entry in data.get("Mapping", []):
    k = entry.get("Key")
    v = entry.get("Value")
    if k and v:
        flat[k] = v

# Write simplified version
with open("mapping_flat.json", "w", encoding="utf-8") as f:
    json.dump(flat, f, ensure_ascii=False, indent=2)

print("✅ Created mapping_flat.json with", len(flat), "entries.")
