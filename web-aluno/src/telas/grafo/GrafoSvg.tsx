import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent } from 'react'
import type { ConceitoAluno } from '../../api/tipos'
import { estados } from '../../componentes/SeloEstado'
import { fracaoY, layoutEmCamadas } from '../../grafo/layoutEmCamadas'

const LARGURA_CAMADA = 140
const ALTURA_LINHA = 112
const RAIO = 24
const MARGEM = 16

/** Quebra o nome em até 2 linhas de ~14 caracteres (o nome completo aparece no painel). */
function linhasDoRotulo(nome: string): string[] {
  const palavras = nome.split(/\s+/)
  const linhas: string[] = []
  let atual = ''
  for (const p of palavras) {
    if (atual && (atual + ' ' + p).length > 14) {
      linhas.push(atual)
      atual = p
    } else {
      atual = atual ? atual + ' ' + p : p
    }
  }
  if (atual) linhas.push(atual)
  if (linhas.length > 2) return [linhas[0], linhas[1].slice(0, 12) + '…']
  return linhas.map((l) => (l.length > 16 ? l.slice(0, 15) + '…' : l))
}

interface Props {
  conceitos: ConceitoAluno[]
  selecionado: string | null
  aoSelecionar: (nome: string) => void
}

export function GrafoSvg({ conceitos, selecionado, aoSelecionar }: Props) {
  const { posicoes, largura, altura } = useMemo(() => {
    const layout = layoutEmCamadas(new Map(conceitos.map((c) => [c.nome, c.prerequisitos])))
    const totalCamadas = Math.max(1, ...[...layout.values()].map((p) => p.totalCamadas))
    const maiorCamada = Math.max(1, ...[...layout.values()].map((p) => p.tamanhoCamada))
    const altura = maiorCamada * ALTURA_LINHA + 2 * MARGEM
    const posicoes = new Map(
      [...layout].map(([nome, p]) => [
        nome,
        { x: MARGEM + (p.camada + 0.5) * LARGURA_CAMADA, y: MARGEM + fracaoY(p) * (altura - 2 * MARGEM) - 10 },
      ]),
    )
    return { posicoes, largura: totalCamadas * LARGURA_CAMADA + 2 * MARGEM, altura }
  }, [conceitos])

  // Arrastar com o mouse; no celular o próprio rolar do contêiner já arrasta.
  const area = useRef<HTMLDivElement>(null)
  const arrasto = useRef<{ x: number; y: number; esq: number; topo: number } | null>(null)
  function inicio(e: PointerEvent) {
    if (e.pointerType !== 'mouse' || !area.current) return
    arrasto.current = { x: e.clientX, y: e.clientY, esq: area.current.scrollLeft, topo: area.current.scrollTop }
  }
  function mover(e: PointerEvent) {
    const a = arrasto.current
    if (!a || !area.current) return
    area.current.scrollLeft = a.esq - (e.clientX - a.x)
    area.current.scrollTop = a.topo - (e.clientY - a.y)
  }
  const fim = () => (arrasto.current = null)

  // Degradê nas bordas enquanto houver grafo escondido daquele lado.
  const [bordas, setBordas] = useState({ esquerda: false, direita: false })
  const medirBordas = useCallback(() => {
    const a = area.current
    if (!a) return
    setBordas({ esquerda: a.scrollLeft > 4, direita: a.scrollLeft + a.clientWidth < a.scrollWidth - 4 })
  }, [])
  useEffect(() => {
    medirBordas()
    window.addEventListener('resize', medirBordas)
    return () => window.removeEventListener('resize', medirBordas)
  }, [medirBordas, largura])

  // Conceito escolhido (no grafo ou na lista) fica visível no desenho.
  useEffect(() => {
    const a = area.current
    const pos = selecionado ? posicoes.get(selecionado) : undefined
    if (!a || !pos) return
    a.scrollTo({
      left: pos.x - a.clientWidth / 2,
      top: pos.y - a.clientHeight / 2,
      behavior: 'smooth',
    })
  }, [selecionado, posicoes])

  const ligadosAoSelecionado = (de: string, para: string) =>
    selecionado !== null && (de === selecionado || para === selecionado)

  return (
    <div className={`grafo-moldura${bordas.esquerda ? ' mais-esquerda' : ''}${bordas.direita ? ' mais-direita' : ''}`}>
      <div
        ref={area}
        className="grafo-area"
        onScroll={medirBordas}
        onPointerDown={inicio}
        onPointerMove={mover}
        onPointerUp={fim}
        onPointerLeave={fim}
      >
        <svg
          width={largura}
          height={altura}
          viewBox={`0 0 ${largura} ${altura}`}
          role="group"
          aria-label="Mapa do conhecimento: cada seta vai de um pré-requisito para o conceito que depende dele"
        >
          <defs>
            <marker
              id="seta"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" className="grafo-ponta" />
            </marker>
            <marker
              id="seta-forte"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" className="grafo-ponta forte" />
            </marker>
          </defs>

          {conceitos.flatMap((c) =>
            c.prerequisitos
              .filter((p) => posicoes.has(p) && p !== c.nome)
              .map((p) => {
                const a = posicoes.get(p)!
                const b = posicoes.get(c.nome)!
                const x1 = a.x + RAIO + 2
                const x2 = b.x - RAIO - 4
                const meio = (x1 + x2) / 2
                const forte = ligadosAoSelecionado(p, c.nome)
                return (
                  <path
                    key={`${p}→${c.nome}`}
                    d={`M${x1},${a.y} C${meio},${a.y} ${meio},${b.y} ${x2},${b.y}`}
                    className={`grafo-seta${forte ? ' forte' : ''}`}
                    markerEnd={`url(#${forte ? 'seta-forte' : 'seta'})`}
                  />
                )
              }),
          )}

          {conceitos.map((c) => {
            const pos = posicoes.get(c.nome)
            if (!pos) return null
            const e = estados[c.estado]
            const sel = c.nome === selecionado
            const linhas = linhasDoRotulo(c.nome)
            return (
              <g
                key={c.nome}
                className="no-grafo"
                transform={`translate(${pos.x},${pos.y})`}
                role="button"
                tabIndex={0}
                aria-pressed={sel}
                aria-label={`${c.nome}: ${e.rotulo}`}
                onClick={() => aoSelecionar(c.nome)}
                onKeyDown={(ev) => {
                  if (ev.key === 'Enter' || ev.key === ' ') {
                    ev.preventDefault()
                    aoSelecionar(c.nome)
                  }
                }}
              >
                {/* Área de toque maior que o círculo. */}
                <rect
                  x={-LARGURA_CAMADA / 2 + 8}
                  y={-RAIO - 6}
                  width={LARGURA_CAMADA - 16}
                  height={ALTURA_LINHA - 8}
                  fill="transparent"
                />
                <circle r={RAIO + 10} className="no-anel-foco" />
                {sel && <circle r={RAIO + 6} className="no-anel" />}
                <circle r={RAIO} className={`no-circulo ${c.estado}`} />
                <text textAnchor="middle" dominantBaseline="central" className={`no-simbolo ${c.estado}`}>
                  {e.simbolo}
                </text>
                <text textAnchor="middle" y={RAIO + 20} className="no-nome">
                  {linhas.map((l, i) => (
                    <tspan key={i} x={0} dy={i === 0 ? 0 : 18}>
                      {l}
                    </tspan>
                  ))}
                </text>
              </g>
            )
          })}
        </svg>
      </div>
    </div>
  )
}
