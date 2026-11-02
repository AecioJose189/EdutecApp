import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Carregando } from '../componentes/Carregando'
import { useSessao } from './SessaoProvider'

/** Sem login, manda para /entrar lembrando a URL de origem (convite ou QR) para voltar depois. */
export function RotaProtegida() {
  const { usuario } = useSessao()
  const local = useLocation()
  if (usuario === undefined) return <Carregando texto="Verificando sua conta…" />
  if (usuario === null) {
    const volta = local.pathname + local.search
    return <Navigate to={`/entrar?volta=${encodeURIComponent(volta)}`} replace />
  }
  return <Outlet />
}
