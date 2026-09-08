# INAT EAD Backend

API da plataforma de educação a distância do INAT Paranaguá, instituição do terceiro setor que atua com formação profissional, inclusão social e desenvolvimento de jovens.

## Identidade do projeto

- Artefato Maven: `dev.lourencogabriel:inat-ead-backend`
- Aplicação Spring: `inat-ead-backend`
- Pacote Java base: `dev.lourencogabriel.inatead`
- Classe de inicialização: `InatEadApplication`
- Banco de dados padrão do ambiente Docker: `inat_ead`

## Escopo atual

O backend contém a autenticação JWT existente e a API do modelo educacional on-premises:

- identidade, pessoas, endereços, usuários, papéis e responsáveis;
- organizações do tipo empregador ou escola e seus vínculos;
- contratos de aprendizagem, documentos e arquivos armazenados no filesystem;
- turmas, matrículas, aulas `ONSITE` ou `ONLINE` e atividades;
- chamada esperada por aula (`lesson_participants`) e frequência efetiva (`attendance_records`);
- remanejo pontual de um aprendiz para uma aula de outra turma, sem alterar sua matrícula de origem;
- entregas de atividades, anexos, notificações e estado por destinatário/canal.

As tabelas usam nomes em inglês, chaves ULID nos agregados principais e instantes em UTC.

## Autorização

O Spring Method Security aplica RBAC e autorização por objeto. Existem somente quatro papéis de sistema:

- `ADMIN`: administração completa e toda a operação pedagógica, cadastral, documental, contratual e de comunicação;
- `INSTRUCTOR`: somente aulas atribuídas, seus participantes, chamadas, frequências, atividades e correções;
- `LEARNER`: próprio percurso, aulas, entregas, arquivos contextuais, contrato e notificações;
- `EMPLOYER_MANAGER`: leitura da empresa empregadora com vínculo ativo, de seus contratos e dos aprendizes relacionados.

O catálogo é fechado: a API rejeita a criação ou concessão de qualquer outro código. Não existem os papéis genéricos `USER`, de equipe pedagógica ou de gestor escolar. Uma conta sem concessão explícita não recebe autoridade implícita e fica sem acesso aos módulos de domínio; ela ainda pode consultar a própria conta e notificações quando a autorização for por identidade.

| Fluxo/módulo | `ADMIN` | `INSTRUCTOR` | `LEARNER` | `EMPLOYER_MANAGER` |
| --- | --- | --- | --- | --- |
| Pessoas, responsáveis e documentos | Gerência completa | Dados mínimos de aprendizes de suas aulas | Próprio contexto quando exposto pela rota | Dados mínimos de aprendizes contratados pela empresa |
| Aprendizes | Gerência completa | Leitura dos participantes de suas aulas | Próprio registro | Leitura dos vinculados por contrato à empresa |
| Empresas, escolas e vínculos | Gerência completa | Sem acesso direto | Sem acesso direto | Leitura somente da empresa com vínculo ativo |
| Contratos | Gerência completa | Sem acesso | Leitura dos próprios | Leitura dos relacionados à empresa |
| Turmas e matrículas | Gerência completa | Contexto obtido pelas aulas atribuídas | Contexto obtido pelas aulas em que participa | Sem acesso direto |
| Aulas, participantes e frequência | Gerência completa | Gerência das aulas atribuídas | Leitura da própria participação/frequência | Sem acesso |
| Atividades e entregas | Gerência completa | Criação/correção nas aulas atribuídas | Próprias entregas | Sem acesso |
| Comunicações | Criação e acompanhamento global | Caixa própria | Caixa própria | Caixa própria |
| Usuários, catálogo e concessões | Gerência completa | Somente a própria conta/papéis | Somente a própria conta/papéis | Somente a própria conta/papéis |

Em um banco novo, o primeiro papel `ADMIN` deve ser atribuído por um procedimento de bootstrap operacional no banco. A API não promove automaticamente o primeiro usuário e não oferece uma rota pública de elevação de privilégio.

## Organização da API

Os endpoints ficam sob `/api` e seguem os módulos `people`, `users`, `roles`, `learners`, `organizations`, `contracts`, `document-types`, `person-documents`, `cohorts`, `cohort-enrollments`, `lessons`, `lesson-participants`, `attendance-records`, `activities`, `activity-submissions`, `notifications` e `notification-recipients`.

Para paginação, as listagens administrativas aceitam `page` e `size` (máximo 100). Operações específicas incluem geração da chamada esperada, remanejo entre aulas do mesmo dia, correção de frequência, avaliação de entregas, caixa pessoal de notificações e transições de entrega.

### Conflitos de horário das aulas

Duas aulas se sobrepõem quando uma começa antes do término da outra e termina depois do início da outra. Aulas adjacentes são permitidas: uma pode começar exatamente no instante em que a anterior termina.

- Um instrutor não pode possuir duas aulas não canceladas sobrepostas;
- uma turma não pode possuir duas aulas não canceladas sobrepostas;
- um aprendiz não pode possuir duas participações não canceladas em aulas sobrepostas, mesmo que pertençam a turmas diferentes;
- a regra do aprendiz é aplicada na inclusão manual, geração da chamada, remanejo e reativação de participantes;
- ao alterar o horário de uma aula, todos os participantes ativos já vinculados são revalidados;
- aulas e participações canceladas são desconsideradas.

Cada aula pode informar `externalLessonUrl`, independentemente da modalidade. O campo é opcional, aceita no máximo 2.048 caracteres e, quando preenchido, precisa ser uma URL pública com esquema `http://` ou `https://`. Ele representa o conteúdo externo reproduzível da aula e não substitui `meetingUrl`, que continua sendo o link obrigatório de acesso às aulas `ONLINE`.

### Ciclo acadêmico baseado no horário

Um agendador executa a cada minuto por padrão e reconcilia os estados persistidos com o relógio da aplicação:

- aulas `SCHEDULED` passam para `IN_PROGRESS` quando `startsAt <= agora < endsAt`;
- aulas `SCHEDULED` ou `IN_PROGRESS` passam diretamente para `COMPLETED` quando `endsAt <= agora`, inclusive após uma indisponibilidade da aplicação;
- atividades `PUBLISHED` passam para `CLOSED` quando `dueAt <= agora`;
- aulas e atividades `CANCELLED`, atividades `DRAFT` e estados terminais não são alterados;
- `DRAFT` nunca é publicado automaticamente: a publicação continua sendo uma decisão explícita.

Antes de fechar as atividades, o mesmo ciclo processa a janela de lembrete de três horas. O intervalo e o tamanho do lote são configuráveis. O processamento de cada atividade usa bloqueio pessimista e grava `dueReminderProcessedAt`, garantindo no máximo um lembrete por prazo mesmo após reinício ou com mais de uma instância. Se `dueAt` mudar, ou a atividade for publicada novamente, essa marca é limpa para o novo prazo.

### Cadastro de usuários

`POST /api/auth/register` cria somente a conta de acesso. O e-mail é normalizado para minúsculas e precisa coincidir com o `contactEmail` de uma pessoa já cadastrada; essa pessoa é vinculada obrigatoriamente ao novo usuário em uma relação um-para-um. O e-mail de contato das pessoas é único quando informado.

- Se não existir uma pessoa com o e-mail fornecido, a API responde `422 Unprocessable Entity`;
- se a pessoa já estiver vinculada a outra conta, responde `409 Conflict`;
- se o e-mail de login já estiver em uso, responde `409 Conflict`.

Nas rotas administrativas de usuários, `personId` também é obrigatório. O cadastro de usuário nunca cria uma pessoa implicitamente.

A existência da pessoa elimina a ideia de uma “conta em análise”, mas não permite inferir com segurança se ela é administradora, instrutora, aprendiz ou gestora. Por isso, papel e identidade continuam separados: a conta recebe somente papéis explicitamente concedidos por um administrador. `INVITED` não é um papel nem uma análise cadastral; é apenas o estado transitório até a confirmação do e-mail.

O cadastro não emite mais JWT imediatamente. Ele cria a conta como `INVITED`, envia um código de seis dígitos e retorna um desafio com `challengeId`, finalidade, e-mail mascarado, expiração e número máximo de tentativas. O código confirma a posse do e-mail e somente `POST /api/auth/challenge/verify` emite os tokens e ativa a conta.

Depois da validação inicial, todo `POST /api/auth/login` bem-sucedido na etapa da senha retorna `202 Accepted` e envia um novo código com finalidade `MFA_LOGIN`. Esse código também precisa ser confirmado em `/api/auth/challenge/verify`. Se uma conta antiga ainda não tiver `emailVerifiedAt`, seu próximo login usa a finalidade `EMAIL_VERIFICATION` antes de liberar tokens.

```json
{
  "challengeId": "01...",
  "code": "123456"
}
```

Cada novo desafio invalida o anterior. Por padrão, o código expira em dez minutos, permite cinco tentativas e fica persistido somente como HMAC-SHA-256 com nonce aleatório. Contas não verificadas também não podem criar ou renovar tokens diretamente.

### Criação de notificações

`POST /api/notifications` exige uma audiência (`USER`, `COHORT` ou `ALL`) e ao menos um canal (`IN_APP`, `EMAIL` ou `PUSH`). A notificação e todos os destinatários resolvidos são persistidos na mesma transação; destinatários não podem ser criados ou excluídos isoladamente em `/api/notification-recipients`.

- `USER`: exige `audience.targetId` com o ID de uma conta ativa;
- `COHORT`: exige o ID da turma e seleciona os aprendizes com matrícula vigente, perfil ativo e conta ativa;
- `ALL`: não aceita `targetId` e seleciona todas as contas ativas.

A audiência é materializada como um retrato no momento da criação. Assim, alterações posteriores na turma não modificam os destinatários de uma notificação já criada.

```json
{
  "notificationType": "ACTIVITY_DUE",
  "title": "Atividade pendente",
  "message": "Há uma atividade aguardando sua entrega.",
  "priority": "NORMAL",
  "audience": {
    "type": "COHORT",
    "targetId": "01K4B3ZQ2M8T7V6W5X4Y3Z2A1B"
  },
  "channels": ["IN_APP", "EMAIL"]
}
```

O `PUT /api/notifications/{id}` altera somente o conteúdo e o agendamento enquanto nenhum destinatário tiver sido processado. Audiência e canais são imutáveis após a criação.

Destinatários do canal `EMAIL` são processados em segundo plano. O dispatcher seleciona notificações disponíveis, bloqueia cada destinatário durante o envio e usa o SMTP da Hostinger. Quando o servidor SMTP aceita a mensagem, o destinatário passa para `SENT`; erros ficam em `FAILED` com motivo seguro e podem retornar a `PENDING` pelo fluxo administrativo para nova tentativa. Como não há webhook de entrega da caixa postal, a aplicação não marca automaticamente e-mails como `DELIVERED`.

### Notificações automáticas de atividades

- Ao criar uma atividade como `PUBLISHED`, ou sempre que uma atividade mudar de outro estado para `PUBLISHED`, todos os alunos ativos da turma vinculada à aula recebem notificações `IN_APP` e `EMAIL`. Se `availableAt` estiver no futuro, ambos os canais aguardam esse instante;
- ao entrar na janela de três horas antes de `dueAt`, é criada uma notificação `ACTIVITY_DUE_SOON`, nos canais `IN_APP` e `EMAIL`, somente para alunos ativos da turma que não possuam entrega em `SUBMITTED`, `LATE` ou `GRADED`; alunos sem entrega ou com entrega `DRAFT`/`RETURNED` recebem o lembrete;
- o lembrete expira em `dueAt`, não é duplicado e não cria uma notificação vazia quando todos já entregaram;
- atividades em `DRAFT`, `CLOSED` ou `CANCELLED` não notificam alunos;
- quando uma entrega muda para `SUBMITTED` ou `LATE`, o usuário ativo com papel de professor vinculado à aula recebe notificações `IN_APP` e `EMAIL`;
- se a turma não possuir alunos ativos, ou o professor não possuir uma conta ativa, o evento principal é mantido sem criar uma notificação sem destinatário.

### Restrições atuais de entrega de atividades

- Existe no máximo uma entrega por combinação de atividade e aluno;
- a atividade precisa estar em `PUBLISHED`; depois do fechamento automático, uma atividade `CLOSED` continua aceitando somente o envio efetivo, convertido em `LATE`, para preservar entregas atrasadas;
- o aluno precisa constar como participante da aula e não pode estar com participação `CANCELLED`;
- o aluno só pode salvar os estados `DRAFT` ou `SUBMITTED`; um envio posterior ao prazo é convertido automaticamente em `LATE`;
- somente entregas `DRAFT` ou `RETURNED` podem ser editadas ou excluídas; para alunos, a autorização de anexos segue a mesma regra, embora atualmente `ADMIN` possa anexar ou remover arquivos independentemente do estado;
- uma entrega `SUBMITTED` ou `LATE` só pode ser avaliada ou devolvida, e uma nota não pode exceder `maxScore`;
- atualmente não há bloqueio para salvar antes de `availableAt`, não há recusa depois de `dueAt` e texto/anexo não são obrigatórios;
- anexos devem ser não vazios, têm limite padrão de 25 MB e nome de até 255 caracteres. Não há lista de MIME types permitidos nem limite de quantidade de anexos.

### Frequência automática em aulas online

Depois de uma entrega mudar para `SUBMITTED` ou `LATE`, o backend verifica a conclusão da aula para aquele aprendiz:

- a regra é executada somente quando `lessons.delivery_mode` é `ONLINE`; aulas `ONSITE` nunca recebem frequência por entrega;
- é necessário existir ao menos uma atividade `PUBLISHED` ou `CLOSED` na aula; atividades `DRAFT` e `CANCELLED` não entram no conjunto obrigatório;
- cada atividade obrigatória precisa possuir uma entrega do aprendiz em `SUBMITTED`, `LATE` ou `GRADED`; `DRAFT` e `RETURNED` ainda não concluem o conjunto;
- quando todas estiverem entregues, é criado um único `attendance_record` com estado `PRESENT`, `checkInAt` igual a `lesson.startsAt` e `checkOutAt` igual a `lesson.endsAt`;
- o usuário autenticado que realizou a última entrega fica registrado em `recordedByUser`;
- o participante é bloqueado durante a verificação para impedir duplicidade em entregas concorrentes;
- uma frequência preexistente, inclusive lançada manualmente, é preservada e nunca sobrescrita pela automação.

O domínio de modalidade aceita exclusivamente `ONSITE` ou `ONLINE` nas requisições de aula.

### Armazenamento de arquivos

O binário é gravado no filesystem e a tabela `files` mantém somente seus metadados. `StoredFile` é uma implementação interna: não existe um CRUD `/api/files`, não é possível fornecer `storageKey`, tamanho ou checksum manualmente, e a chave física nunca é exposta ao cliente. Os endpoints dos recursos criam o metadado e o vínculo na mesma transação do upload:

| Recurso | Upload | Download |
| --- | --- | --- |
| Documento de pessoa | `POST /api/person-documents` | `GET /api/person-documents/{id}/content` |
| Documento de contrato | `POST /api/contract-documents` | `GET /api/contract-documents/{id}/content` |
| Material de atividade | `POST /api/activities/{activityId}/files` | `GET /api/activities/{activityId}/files/{fileId}/content` |
| Anexo de entrega | `POST /api/activity-submissions/{submissionId}/files` | `GET /api/activity-submissions/{submissionId}/files/{fileId}/content` |

Uploads que também possuem dados estruturados usam `multipart/form-data`: a parte `metadata` contém JSON com `Content-Type: application/json`, e a parte `file` contém o binário. Por exemplo:

```bash
curl -X POST http://localhost:8080/api/person-documents \
  -H "Authorization: Bearer <token>" \
  -F 'metadata={"personId":"01...","documentTypeId":1,"documentNumber":"123","issuedOn":"2026-09-04","expiresOn":null,"verificationStatus":"PENDING"};type=application/json' \
  -F 'file=@documento.pdf;type=application/pdf'
```

O upload de uma entrega não possui metadados adicionais e envia apenas a parte `file`. Nas respostas, `file` informa ID, nome original, MIME type, tamanho, SHA-256, autor e data. O download sempre usa `Content-Disposition: attachment` e respeita a autorização do recurso pai.

Durante a gravação, a API gera uma chave aleatória, transmite o conteúdo diretamente ao disco e calcula o SHA-256 na mesma passagem. Um rollback remove o binário recém-criado; ao excluir o recurso ou vínculo, o metadado é removido na transação e o binário somente depois do commit. No Docker Compose, o volume nomeado `file_storage` preserva os dados em `/var/lib/inat/files`.

### Onboarding de aprendizes

`POST /api/learner-onboardings` cria o perfil de pessoa, incluindo seu endereço, e o perfil de aprendiz, além de vincular os responsáveis em uma única transação. Se o CPF já existir, a pessoa é reutilizada sem ter seus dados sobrescritos; a data de nascimento informada deve coincidir com o cadastro existente. Aprendizes menores de 18 anos devem receber ao menos um vínculo marcado como responsável legal.

As pessoas usadas como responsáveis precisam existir previamente. Os endpoints individuais de pessoas, aprendizes e responsáveis permanecem disponíveis para manutenção administrativa. Endereços são componentes obrigatórios de pessoas e organizações e não possuem endpoint próprio.

## Tecnologias

- Java 21
- Spring Boot
- Spring Security e OAuth 2.0 Resource Server
- Spring Mail
- Spring Data JPA
- MySQL
- Maven
- JUnit, Mockito, H2 e Testcontainers

## Configuração

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Sim | URL JDBC do MySQL |
| `SPRING_DATASOURCE_USERNAME` | Sim | Usuário do banco de dados |
| `SPRING_DATASOURCE_PASSWORD` | Sim | Senha do banco de dados |
| `API_SECURITY_JWT_SECRET` | Sim | Chave de assinatura JWT com pelo menos 32 caracteres |
| `API_SECURITY_JWT_ISSUER` | Sim | URL que identifica o emissor dos tokens |
| `API_SECURITY_REFRESH_COOKIE_SECURE` | Não | Exige HTTPS no cookie de refresh; padrão: `true` |
| `EMAIL` | Sim | Endereço completo da caixa Hostinger, usado como usuário SMTP e remetente |
| `EMAIL_PASS` | Sim | Senha da caixa de e-mail Hostinger |
| `SMTP_HOST` | Não | Servidor SMTP; padrão: `smtp.hostinger.com` |
| `SMTP_PORT` | Não | Porta SMTP; padrão: `465` |
| `SMTP_SSL_ENABLED` | Não | Habilita SSL implícito; padrão: `true` |
| `SMTP_STARTTLS_ENABLED` | Não | Habilita STARTTLS, normalmente com porta `587`; padrão: `false` |
| `AUTH_CHALLENGE_TTL` | Não | Validade dos códigos de autenticação; padrão: `10m` |
| `AUTH_CHALLENGE_MAX_ATTEMPTS` | Não | Tentativas permitidas por desafio; padrão: `5` |
| `NOTIFICATION_EMAIL_ENABLED` | Não | Habilita o dispatcher de notificações por e-mail; padrão: `true` |
| `ACADEMIC_LIFECYCLE_ENABLED` | Não | Habilita o agendador de estados acadêmicos e lembretes; padrão: `true` |
| `ACADEMIC_LIFECYCLE_INTERVAL` | Não | Intervalo em milissegundos entre execuções; padrão: `60000` |
| `ACADEMIC_REMINDER_BATCH_SIZE` | Não | Máximo de atividades verificadas por ciclo para o lembrete; padrão: `200` (limite: `1000`) |
| `FILE_STORAGE_ROOT` | Não | Diretório do filesystem; padrão local: `./data/files` |
| `FILE_STORAGE_MAX_SIZE` | Não | Tamanho máximo de cada arquivo; padrão: `25MB` |
| `FILE_STORAGE_MAX_REQUEST_SIZE` | Não | Tamanho máximo da requisição multipart; padrão: `26MB` |
| `SERVER_PORT` | Não | Porta HTTP; padrão: `8080` |

O padrão da Hostinger usa `smtp.hostinger.com:465` com SSL. Para a alternativa STARTTLS, configure `SMTP_PORT=587`, `SMTP_SSL_ENABLED=false`, `SMTP_STARTTLS_ENABLED=true` e `SMTP_STARTTLS_REQUIRED=true`.

O [repositório de infraestrutura](https://github.com/GabrielLourenco1912/infra-inat) contém o `.env.example` usado pelo Docker Compose. Copie-o para `.env` e substitua os valores sensíveis antes de iniciar os serviços.

## Execução

Na raiz do repositório:

```bash
docker compose up --build
```

Para executar apenas os testes do backend:

```bash
./mvnw test
```
