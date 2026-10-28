import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { RotaProtegida } from './sessao/RotaProtegida'
import { SessaoProvider } from './sessao/SessaoProvider'
import { Convite } from './telas/Convite'
import { Entrar } from './telas/Entrar'
import { MinhasTurmas } from './telas/MinhasTurmas'
import { NaoEncontrada } from './telas/NaoEncontrada'
import { Atividade } from './telas/atividade/Atividade'
import { MeuGrafo } from './telas/grafo/MeuGrafo'

export function App() {
  return (
    <SessaoProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/entrar" element={<Entrar />} />
          <Route element={<RotaProtegida />}>
            <Route path="/" element={<MinhasTurmas />} />
            <Route path="/convite/:codigo" element={<Convite />} />
            <Route path="/s/:token" element={<Atividade />} />
            <Route path="/turmas/:turmaId/grafo" element={<MeuGrafo />} />
          </Route>
          <Route path="*" element={<NaoEncontrada />} />
        </Routes>
      </BrowserRouter>
    </SessaoProvider>
  )
}
