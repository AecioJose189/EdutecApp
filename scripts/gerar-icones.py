#!/usr/bin/env python3
"""Gera os ícones dos instaladores do desktop a partir do símbolo do Lumini.

    python3 scripts/gerar-icones.py [svg] [--fundo "#FEFEFE"]

Entrada padrão: docs/design/logo/lumini-simbolo-primaria.svg.
Saída em desktop/icones/ (ligados em desktop/build.gradle.kts, nativeDistributions):
  - lumini.png  (512 px, Linux/.deb)
  - lumini.ico  (16 a 256 px, Windows/.msi)
  - lumini.icns (16 a 1024 px, macOS/.dmg)
  - com --conferir, também lumini-<n>.png de cada tamanho em desktop/icones/conferir/ (fora do git).

Cada tamanho é desenhado direto do vetor (não é reduzido de uma imagem grande). O símbolo fica sobre um
quadrado de cantos arredondados claro: sem ele, a haste em primaria some na barra de tarefas escura.
Quando o grupo de design entregar a versão simplificada para tamanhos pequenos, passe-a com --pequeno
(usada de 16 a 32 px) e rode de novo.

Precisa de: Python 3, Pillow e librsvg. No Ubuntu/Debian:
    sudo apt install python3-pil python3-gi gir1.2-rsvg-2.0
Sem o PyGObject, usa o rsvg-convert (sudo apt install librsvg2-bin; macOS: brew install librsvg).
"""
import argparse
import io
import os
import re
import shutil
import subprocess
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG_PADRAO = os.path.join(RAIZ, "docs/design/logo/lumini-simbolo-primaria.svg")
SAIDA = os.path.join(RAIZ, "desktop/icones")
TAMANHOS_ICO = [16, 24, 32, 48, 64, 128, 256]
TAMANHOS_ICNS = [16, 32, 64, 128, 256, 512, 1024]
# Proporções do ícone: canto do quadrado e altura do símbolo em relação ao lado.
RAIO = 0.22
ALTURA_SIMBOLO = 0.74

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Falta o Pillow: sudo apt install python3-pil (ou pip install pillow)")


def com_tamanho(svg: str, largura: int, altura: int) -> str:
    """Põe width/height no <svg> (os arquivos do design só têm viewBox)."""
    svg = re.sub(r'(<svg\b[^>]*?)\s(width|height)="[^"]*"', r"\1", svg, count=2)
    return svg.replace("<svg", f'<svg width="{largura}" height="{altura}"', 1)


def rasterizar(svg: str, largura: int, altura: int) -> "Image.Image":
    texto = com_tamanho(svg, largura, altura)
    try:
        import gi

        gi.require_version("Rsvg", "2.0")
        from gi.repository import Rsvg

        pixbuf = Rsvg.Handle.new_from_data(texto.encode()).get_pixbuf()
        ok, dados = pixbuf.save_to_bufferv("png", [], [])
        return Image.open(io.BytesIO(dados)).convert("RGBA")
    except (ImportError, ValueError):
        pass
    if shutil.which("rsvg-convert"):
        png = subprocess.run(["rsvg-convert", "-w", str(largura), "-h", str(altura)], input=texto.encode(),
                             capture_output=True, check=True).stdout
        return Image.open(io.BytesIO(png)).convert("RGBA")
    sys.exit("Falta a librsvg. Instale: sudo apt install python3-gi gir1.2-rsvg-2.0 "
             "(ou librsvg2-bin; no macOS: brew install librsvg)")


def proporcao(svg: str) -> float:
    m = re.search(r'viewBox="\s*[-\d.]+[\s,]+[-\d.]+[\s,]+([\d.]+)[\s,]+([\d.]+)', svg)
    if not m:
        sys.exit("O SVG precisa de viewBox")
    return float(m.group(1)) / float(m.group(2))


def icone(svg: str, lado: int, fundo: str) -> "Image.Image":
    # Desenha em 4x e reduz só a borda do quadrado; o símbolo vem do vetor no tamanho final.
    escala = 4
    base = Image.new("RGBA", (lado * escala, lado * escala), (0, 0, 0, 0))
    ImageDraw.Draw(base).rounded_rectangle(
        (0, 0, lado * escala - 1, lado * escala - 1), radius=int(lado * escala * RAIO), fill=fundo)
    base = base.resize((lado, lado), Image.LANCZOS)
    altura = max(1, round(lado * ALTURA_SIMBOLO))
    largura = max(1, round(altura * proporcao(svg)))
    simbolo = rasterizar(svg, largura, altura)
    base.alpha_composite(simbolo, ((lado - largura) // 2, (lado - altura) // 2))
    return base


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("svg", nargs="?", default=SVG_PADRAO)
    p.add_argument("--pequeno", help="SVG simplificado para 16 a 32 px (quando o design entregar)")
    p.add_argument("--fundo", default="#FEFEFE", help="cor do quadrado (padrão: superficie do tema claro)")
    p.add_argument("--conferir", action="store_true", help="salva também um PNG de cada tamanho")
    a = p.parse_args()

    svg = open(a.svg, encoding="utf-8").read()
    pequeno = open(a.pequeno, encoding="utf-8").read() if a.pequeno else svg
    os.makedirs(SAIDA, exist_ok=True)

    imagens = {}
    for lado in sorted(set(TAMANHOS_ICO + TAMANHOS_ICNS)):
        imagens[lado] = icone(pequeno if lado <= 32 else svg, lado, a.fundo)
        if a.conferir:
            os.makedirs(os.path.join(SAIDA, "conferir"), exist_ok=True)
            imagens[lado].save(os.path.join(SAIDA, "conferir", f"lumini-{lado}.png"))

    imagens[512].save(os.path.join(SAIDA, "lumini.png"))
    maior_ico = imagens[256]
    maior_ico.save(os.path.join(SAIDA, "lumini.ico"), sizes=[(n, n) for n in TAMANHOS_ICO],
                   append_images=[imagens[n] for n in TAMANHOS_ICO if n != 256])
    imagens[1024].save(os.path.join(SAIDA, "lumini.icns"),
                       append_images=[imagens[n] for n in TAMANHOS_ICNS if n != 1024])
    print("Ícones gerados em", os.path.relpath(SAIDA, RAIZ))


if __name__ == "__main__":
    main()
