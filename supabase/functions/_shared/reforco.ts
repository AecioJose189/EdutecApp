// Atividade de reforço (fase 5): partes puras da gerar-atividade, com testes em reforco_test.ts.
//
// O reforço é gerado a partir do PDF de onde o conceito principal veio, com questões FOCADAS nos
// conceitos escolhidos e diferentes das já publicadas na turma. Vai para a IA só conteúdo do professor:
// nomes de conceitos e enunciados publicados (RN13: nada de aluno).

/** Limites do que vai para o prompt (o pedido não cresce sem controle). */
export const MAX_CONCEITOS_FOCO = 8;
export const MAX_ENUNCIADOS = 40;
const MAX_TAMANHO_NOME = 120;
const MAX_TAMANHO_ENUNCIADO = 400;

/** Mesma regra de public.normalizar_conceito: minúsculas, sem acento, espaços colapsados. */
export function normalizarConceito(texto: string): string {
  return texto.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/\s+/g, " ").trim();
}

/**
 * Lê o campo `conceitos_foco` do formulário: JSON com uma lista de nomes. Tira espaços e repetidos
 * (pelo nome normalizado), mantém a ordem (o 1º é o principal). null se o campo não veio.
 */
export function lerConceitosFoco(valor: string | null): string[] | null {
  if (valor == null || valor.trim() === "") return null;
  let lista: unknown;
  try {
    lista = JSON.parse(valor);
  } catch {
    throw new Error('"conceitos_foco" deve ser uma lista JSON de nomes.');
  }
  if (!Array.isArray(lista) || lista.some((c) => typeof c !== "string")) {
    throw new Error('"conceitos_foco" deve ser uma lista JSON de nomes.');
  }
  const vistos = new Set<string>();
  const foco: string[] = [];
  for (const bruto of lista as string[]) {
    const nome = bruto.trim();
    const chave = normalizarConceito(nome);
    if (!nome || vistos.has(chave)) continue;
    if (nome.length > MAX_TAMANHO_NOME) throw new Error(`Nome de conceito com mais de ${MAX_TAMANHO_NOME} caracteres.`);
    vistos.add(chave);
    foco.push(nome);
  }
  if (foco.length === 0) throw new Error("Escolha ao menos um conceito.");
  if (foco.length > MAX_CONCEITOS_FOCO) throw new Error(`Escolha no máximo ${MAX_CONCEITOS_FOCO} conceitos.`);
  return foco;
}

/**
 * Chave do cache (RN12) de um reforço: conceitos do foco (normalizados, sem ordem) + enunciados já
 * publicados. Publicado um reforço, os enunciados mudam e o próximo pedido gera questões novas.
 */
export async function chaveFoco(foco: string[], enunciados: string[]): Promise<string> {
  const texto = JSON.stringify({
    conceitos: [...new Set(foco.map(normalizarConceito))].sort(),
    enunciados: enunciados.map((e) => e.trim()).sort(),
  });
  const resumo = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto)));
  return Array.from(resumo, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Trecho do prompt com o foco e as questões a não repetir. */
export function blocoReforco(foco: string[], enunciados: string[]): string {
  const conceitos = foco.map((c) => `  - ${JSON.stringify(c)}`).join("\n");
  const existentes = enunciados.slice(0, MAX_ENUNCIADOS).map((e, i) => {
    const t = e.replace(/\s+/g, " ").trim();
    return `  ${i + 1}. ${t.length > MAX_TAMANHO_ENUNCIADO ? t.slice(0, MAX_TAMANHO_ENUNCIADO) + "…" : t}`;
  }).join("\n");
  return `
ATIVIDADE DE REFORÇO
- Esta é uma atividade de reforço: a turma está com dificuldade nos conceitos abaixo. TODAS as questões
  devem avaliar pelo menos um deles; em "conceitos" de cada questão, use o nome EXATAMENTE como está aqui.
${conceitos}
- Na lista "conceitos" da atividade, inclua esses conceitos com os mesmos nomes (e, se ajudar, os
  pré-requisitos deles que o material ensina).
- Vá do mais básico ao mais elaborado, para ajudar quem ainda não domina o conceito.
${existentes ? `- Estas questões JÁ foram aplicadas à turma. Não as repita nem as reformule: crie situações,
  exemplos e perguntas diferentes.
${existentes}
` : ""}`;
}

/** Problemas de foco: cada questão precisa citar ao menos um conceito do foco. */
export function validarFoco(questoes: { conceitos?: string[] }[], foco: string[]): string[] {
  const alvo = new Set(foco.map(normalizarConceito));
  const problemas: string[] = [];
  questoes.forEach((q, i) => {
    if (!(q.conceitos ?? []).some((c) => alvo.has(normalizarConceito(c)))) {
      problemas.push(`questão ${i + 1} não avalia nenhum dos conceitos escolhidos`);
    }
  });
  return problemas;
}
