# Consultas paginadas do portal

O portal usa `GET /api/search/{resource}?view=summary`, inclusive quando `q` está vazio. A autorização, os filtros, a contagem e `LIMIT/OFFSET` são aplicados no banco. A resposta mantém `ApiResponse<PageResponse<...>>`: `content`, `page`, `size`, `totalElements`, `totalPages`, `first` e `last`.

- A API começa em `page=0`; as URLs do portal começam em 1.
- Listas e históricos exibem 20 itens; a API aceita até 100. Seletores retornam até 5 opções.
- `q` aceita até 160 caracteres; `status` recebe o código do enum do recurso. Filtros incompatíveis ou valores inválidos retornam 400.
- As consultas de resumo não incluem históricos nem respostas completas das entregas. Relações necessárias à linha vêm projetadas ou são resolvidas em lote para os IDs da página.
- Os endpoints antigos mantêm suas representações. `view=summary` também pode ser usado nos detalhes de contratos, turmas e documentos pessoais e nas mutações consumidas pelo portal para omitir coleções históricas.

## Recursos e filtros

| Telas | Leitura utilizada |
| --- | --- |
| Painel inicial | Contadores e listas limitadas de `/api/dashboard`; contagem de avisos compartilhada com o layout |
| Pessoas e aprendizes | Páginas de `people` e `learners`, com nomes de empresa/turma projetados |
| Contratos, organizações e turmas | Páginas de `contracts`, `organizations` e `cohorts` |
| Agenda e aulas | Páginas de `lessons` com datas, modalidade e escopo do usuário |
| Atividades | Páginas de `activities`, com situação da entrega e quantidade para corrigir |
| Dossiê do aprendiz | Coleções por `learnerId`, carregadas na aba aberta: contratos, responsáveis, matrículas, aulas, atividades e documentos |
| Detalhe da organização | Resumo de contagens; contratos, aprendizes e vínculos paginados por organização |
| Detalhe da turma | Resumo de vagas; matrículas e aulas paginadas por turma |
| Detalhe do contrato | Documentos, versões e históricos paginados por contrato |
| Detalhe da aula | Participantes, chamada, atividades e arquivos paginados por aula |
| Detalhe da atividade | Entregas paginadas; resposta e anexos apenas da entrega selecionada |
| Documentos expirados | Página com nome e vínculo do proprietário, sem consultas por documento |
| Avisos e comunicações | Filtros globais de leitura, prioridade e pesquisa; falhas de entrega em página própria |
| Administração | Usuários com papéis resolvidos em lote; tipos, papéis e mensagens com consulta paginada |

As telas de conta e acesso não carregam coleções de domínio. Cidades e opções fixas de navegação continuam locais.

| Recursos | Filtros adicionais |
| --- | --- |
| `people` | `personType` |
| `learners` | `personId`, `organizationId` |
| `contracts` | `learnerId`, `organizationId` |
| `lessons` | `cohortId`, `startDate`, `endDate`, `deliveryMode` |
| `activities` | `lessonId`, `learnerId`, `workflow=TODO/REVIEW/RETURNED/SUBMITTED/GRADED` |
| `cohort-enrollments` | `cohortId`, `contractId`, `learnerId` |
| `organization-memberships` | `organizationId` |
| `learner-guardians` | `learnerId`, `status=PRIMARY/LEGAL/OTHER` |
| `learner-lessons` | `learnerId` |
| `lesson-participants`, `lesson-attendance` | `lessonId`, `participationType`, `attendanceStatus` (`MISSING` para ausência de registro) |
| `activity-submissions` | `activityId`, `learnerId` |
| `person-documents` | `personId`, `documentTypeId`, `verificationStatus` |
| `contract-documents` | `contractId`, `documentTypeId`, `current=true/false` |
| `notifications` | `priority` |
| `notification-recipients` | `notificationId`, `deliveryStatus`, `channel`, `priority`, `read`, `mine=true` |
| `status-history` | `aggregateType=CONTRACT/COHORT/COHORT_ENROLLMENT`, `aggregateId` |
| `school-history` | `contractId` |
| `document-history` | `documentId` |
| `lesson-files`, `activity-files`, `submission-files` | `contextId` |

`organizations`, `cohorts`, `users`, `roles` e `document-types` também usam o contrato paginado. A caixa pessoal usa `/api/notification-recipients/me?view=summary&channel=IN_APP&read=false`. A identidade vem da sessão, nunca dos filtros enviados pelo navegador.

As datas são inclusivas no fuso `America/Sao_Paulo` e convertidas em limites UTC para a consulta. A ordenação inclui uma chave única para desempatar. Os filtros específicos de cada coleção permanecem na URL; mudar um filtro reinicia sua página.

## Contagens e resumos

- `/api/notification-recipients/me/unread-count` retorna somente `{count}`, dentro do envelope existente, usando `COUNT` para avisos internos não lidos e disponíveis. Painel e layout compartilham a consulta durante a renderização.
- `/api/dashboard` traz contadores, até cinco próximas aulas e quatro avisos disponíveis.
- `/api/organizations/{id}/summary` conta contratos e aprendizes no mesmo escopo das listas.
- `/api/lessons/{id}/summary` conta participantes, presenças e atividades autorizadas.
- `/api/contracts/{id}/document-types/{typeId}/summary` retorna `nextVersion` e `currentId`.
- `/api/lookups/document-types?purpose=person-document&contextId={personId}` pesquisa tipos ainda disponíveis para a pessoa. `purpose=contract-document` restringe os tipos a contratos.
- `/api/lessons/{id}/files/summary` e `/api/activities/{id}/files/summary` retornam `nextSortOrder`.

## Chamada

Rascunhos ficam no navegador somente para os participantes alterados e são preservados entre páginas, filtros e abas da mesma aula.

`GET /api/lessons/{lessonId}/attendance/missing-count?q=...&status=...` conta os participantes elegíveis sem presença. O botão de preenchimento coletivo guarda uma cópia desses filtros e não grava nada nesse momento. A prévia paginada recebe `previewFill` como JSON dos filtros e calcula `matchesPendingFill` no banco.

Ao clicar em Salvar:

1. `POST /api/lessons/{lessonId}/attendance/batch` envia até 100 alterações em `items`, com `values`, `recordId` e `expectedUpdatedAt`. Cada participante recebe uma confirmação ou erro. Repetir uma criação já confirmada no banco não duplica a presença.
2. Havendo falhas individuais, o preenchimento coletivo permanece pendente.
3. `POST /api/lessons/{lessonId}/attendance/fill-missing` recebe os filtros congelados e processa até 100 participantes, retornando `{saved, failed, remaining}`. O front repete enquanto há progresso e registros restantes. Presenças existentes e participantes cancelados são preservados.

O retorno da geração da lista regular, com `view=summary`, contém apenas os contadores.

## Rede e exportações

Consultas JSON do frontend têm prazo de 20 segundos, incluindo a renovação da sessão e uma eventual repetição. Buscas substituídas são canceladas também no proxy do Next. Uploads e downloads mantêm seu comportamento de transferência; não recebem um novo limite no navegador.

Exportações completas continuam sendo operações explícitas. A leitura de páginas de 100 registros no servidor usa no máximo quatro requisições concorrentes; listas do portal não usam esse mecanismo.

## Validação

Os testes de integração executam os mesmos contratos em H2 e MySQL 8.0, incluindo filtros, limites, escopos, índices e a chamada com 125 participantes. Os testes do frontend cobrem filtros na URL, respostas obsoletas, páginas removidas, rascunhos, falhas parciais e uma consulta com atraso de rede simulado de 300 ms.

Na publicação, atualizar primeiro o backend, permitindo que o Flyway aplique a migração V5, e depois o frontend.
