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
              : 'Responda às atividades da aula e veja o seu mapa do conhecimento.'}
        </p>
        <button className="botao largo botao-google" onClick={clicar} disabled={indo}>
          <img src="/google-g.svg" alt="" aria-hidden="true" />
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
