"""Generate the repetitive item-model and localization files for the three sets."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "neoforge/src/main/resources"
themes = {
    "captura": ("Captura", "Capture"),
    "explorador": ("Explorador", "Explorer"),
    "campeon": ("Campeón", "Champion"),
}
pieces = {
    "helmet": ("Casco", "Helmet"),
    "chestplate": ("Pechera", "Chestplate"),
    "leggings": ("Grebas", "Leggings"),
    "boots": ("Botas", "Boots"),
}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

es = {"enchantment.zianutilities.mineria_3x3": "Minería 3×3"}
en = {"enchantment.zianutilities.mineria_3x3": "3×3 Mining"}
for theme, (spanish, english) in themes.items():
    for piece, (piece_es, piece_en) in pieces.items():
        name = f"{theme}_{piece}"
        write(root / f"assets/zianutilities/models/item/{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"zianutilities:item/{name}"},
        })
        es[f"item.zianutilities.{name}"] = f"{piece_es} de {spanish}"
        en[f"item.zianutilities.{name}"] = f"{english} {piece_en}"
write(root / "assets/zianutilities/lang/es_es.json", es)
write(root / "assets/zianutilities/lang/en_us.json", en)
write(root / "data/zianutilities/enchantment/mineria_3x3.json", {
    "description": {"translate": "enchantment.zianutilities.mineria_3x3"},
    "supported_items": "#minecraft:pickaxes",
    "weight": 2,
    "max_level": 1,
    "min_cost": {"base": 25, "per_level_above_first": 0},
    "max_cost": {"base": 50, "per_level_above_first": 0},
    "anvil_cost": 4,
    "slots": ["mainhand"],
    "effects": {},
})
for tag in ("in_enchanting_table", "non_treasure"):
    write(root / f"data/minecraft/tags/enchantment/{tag}.json", {
        "replace": False,
        "values": ["zianutilities:mineria_3x3"],
    })
