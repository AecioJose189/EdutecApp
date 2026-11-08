import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './estilos/global.css'

// Barra do navegador no celular com a cor de fundo do tema (claro ou escuro), sem cor literal fora do tema.
function pintarBarraDoNavegador() {
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'theme-color'
    document.head.appendChild(meta)
  }
  meta.content = getComputedStyle(document.documentElement).getPropertyValue('--fundo').trim()
}
pintarBarraDoNavegador()
window.matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', pintarBarraDoNavegador)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
