import katex from 'katex'
import 'katex/dist/katex.min.css'
import { useMemo } from 'react'

/** Divide o texto em pedaços normais e fórmulas entre `$...$` (`\$` é um cifrão literal). */
export function partesLatex(texto: string): { latex: boolean; conteudo: string }[] {
  const partes: { latex: boolean; conteudo: string }[] = []
  let atual = ''
  let dentro = false
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i]
    if (c === '\\' && texto[i + 1] === '$') {
      atual += dentro ? '\\$' : '$'
      i++
    } else if (c === '$') {
      if (atual) partes.push({ latex: dentro, conteudo: atual })
      atual = ''
      dentro = !dentro
    } else {
      atual += c
    }
  }
  // Cifrão sem par: mostra como texto.
  if (atual) partes.push(dentro ? { latex: false, conteudo: '$' + atual } : { latex: false, conteudo: atual })
  return partes
}

export function TextoLatex({ texto }: { texto: string }) {
  const partes = useMemo(
    () =>
      partesLatex(texto).map((p) =>
        p.latex ? { ...p, html: katex.renderToString(p.conteudo, { throwOnError: false, output: 'html' }) } : p,
      ),
    [texto],
  )
  return (
    <>
      {partes.map((p, i) =>
        'html' in p ? <span key={i} dangerouslySetInnerHTML={{ __html: p.html }} /> : <span key={i}>{p.conteudo}</span>,
      )}
    </>
  )
}
