"""Gera os ícones da PWA a partir do logo existente do app Android."""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "..", "mobile", "src", "main", "res", "drawable", "icone.png")
OUT = os.path.join(ROOT, "icons")

os.makedirs(OUT, exist_ok=True)

src = Image.open(SRC).convert("RGBA")

# Icones "any": o próprio logo circular, redimensionado
for size in (192, 512):
    img = src.resize((size, size), Image.LANCZOS)
    img.save(os.path.join(OUT, f"icon-{size}.png"))

# Icone maskable: fundo preto sólido com o logo com margem de segurança (~80% da área)
for size in (192, 512):
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    inner = int(size * 0.8)
    logo = src.resize((inner, inner), Image.LANCZOS)
    offset = ((size - inner) // 2, (size - inner) // 2)
    canvas.paste(logo, offset, logo)
    canvas.convert("RGB").save(os.path.join(OUT, f"maskable-{size}.png"))

print("icones gerados em", OUT)
