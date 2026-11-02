import { useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { apiSimulada } from '../api/config'
import { Carregando } from '../componentes/Carregando'
import { Marca } from '../componentes/Topo'
import { useSessao } from '../sessao/SessaoProvider'

/** Só aceita caminhos do próprio site, para o `?volta=` não virar redirecionamento aberto. */
function destinoSeguro(volta: string | null): string {
  return volta && volta.startsWith('/') && !volta.startsWith('//') ? volta : '/'
}

export function Entrar() {
  const { usuario, entrar } = useSessao()
  const [parametros] = useSearchParams()
  const destino = destinoSeguro(parametros.get('volta'))
  const [indo, setIndo] = useState(false)
  const [falhou, setFalhou] = useState(false)

  if (usuario === undefined) return <Carregando texto="Verificando sua conta…" />
  if (usuario) return <Navigate to={destino} replace />

  async function clicar() {
    setIndo(true)
    setFalhou(false)
    try {
      await entrar(destino)
    } catch {
      setFalhou(true)
      setIndo(false)
    }
  }

  const vemDeQr = destino.startsWith('/s/')
  const vemDeConvite = destino.startsWith('/convite/')

  return (
    <main className="pagina entrar">
      <div className="entrar-centro">
        <Marca />
        <h1>Acompanhe o que você já domina</h1>
        <p className="corpo">
          {vemDeConvite
            ? 'Entre com sua conta Google para participar da turma.'
            : vemDeQr
              ? 'Entre com sua conta Google para responder à atividade. Depois, leia o QR de novo se o código tiver mudado.'
              : 'Responda às atividades da aula e veja o seu grafo de conceitos.'}
        </p>
        <button className="botao largo botao-google" onClick={clicar} disabled={indo}>
          <svg width="22" height="22" viewBox="0 0 48 48" aria-hidden="true">
            <path
              fill="#FFC107"
              d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"
            />
            <path
              fill="#FF3D00"
              d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"
            />
            <path
              fill="#4CAF50"
              d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"
            />
            <path
              fill="#1976D2"
              d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"
            />
          </svg>
          {indo ? 'Abrindo o Google…' : 'Entrar com Google'}
        </button>
        {falhou && (
          <p className="aviso-erro cartao" role="alert">
            Não foi possível abrir o login do Google. Confira a internet e tente de novo.
          </p>
        )}
        {apiSimulada && <p className="secundario">Modo simulado: o login é de mentira e nenhum dado sai do celular.</p>}
        <p className="secundario">
          <Link to="/privacidade">Política de privacidade</Link>
        </p>
      </div>
    </main>
  )
}
