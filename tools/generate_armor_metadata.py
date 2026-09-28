"""Generate the repetitive item-model and localization files for the three sets."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "neoforge/src/main/resources"
themes = {
    "captura": ("Pikachu", "Pikachu"),
    "explorador": ("Dragonite", "Dragonite"),
    "campeon": ("Lucario", "Lucario"),
}
pieces = {
    "helmet": ("Casco", "Helmet"),
    "chestplate": ("Pechera", "Chestplate"),
    "leggings": ("Grebas", "Leggings"),
    "boots": ("Botas", "Boots"),
    "sword": ("Espada", "Sword"),
    "axe": ("Hacha", "Axe"),
    "pickaxe": ("Pico", "Pickaxe"),
    "shovel": ("Pala", "Shovel"),
    "hoe": ("Azada", "Hoe"),
    "bow": ("Arco", "Bow"),
    "shield": ("Escudo", "Shield"),
}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

es = {"enchantment.zianutilities.mineria_3x3": "Minería 3×3"}
en = {"enchantment.zianutilities.mineria_3x3": "3×3 Mining"}
for theme, (spanish, english) in themes.items():
    for piece, (piece_es, piece_en) in pieces.items():
        name = f"{theme}_{piece}"
        model = {
            "parent": "minecraft:item/handheld" if piece in ("sword", "axe", "pickaxe", "shovel", "hoe") else "minecraft:item/bow" if piece == "bow" else "minecraft:item/generated",
            "textures": {"layer0": f"zianutilities:item/{name}"},
        }
        if piece == "bow":
            model["overrides"] = [
                {"predicate": {"pulling": 1}, "model": f"zianutilities:item/{name}_pulling_0"},
                {"predicate": {"pulling": 1, "pull": .65}, "model": f"zianutilities:item/{name}_pulling_1"},
                {"predicate": {"pulling": 1, "pull": .9}, "model": f"zianutilities:item/{name}_pulling_2"},
            ]
            for phase in range(3):
                write(root / f"assets/zianutilities/models/item/{name}_pulling_{phase}.json", {
                    "parent": "minecraft:item/bow",
                    "textures": {"layer0": f"zianutilities:item/{name}_pulling_{phase}"},
                })
        write(root / f"assets/zianutilities/models/item/{name}.json", model)
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

# 1.21.1 vanilla enchantments select supported items from these tags. The
# ordinary item classes alone do not place modded equipment in those tags.
item_tags = {
    "head_armor": ("helmet",),
    "chest_armor": ("chestplate",),
    "leg_armor": ("leggings",),
    "foot_armor": ("boots",),
    "swords": ("sword",),
    "axes": ("axe",),
    "pickaxes": ("pickaxe",),
    "shovels": ("shovel",),
    "hoes": ("hoe",),
    "enchantable/bow": ("bow",),
    "enchantable/durability": ("bow", "shield"),
}
for tag, suffixes in item_tags.items():
    write(root / f"data/minecraft/tags/item/{tag}.json", {
        "replace": False,
        "values": [f"zianutilities:{theme}_{suffix}" for theme in themes for suffix in suffixes],
    })
