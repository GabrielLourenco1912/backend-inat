# INAT EAD Backend

API da plataforma de educação a distância do INAT Paranaguá, instituição do terceiro setor que atua com formação profissional, inclusão social e desenvolvimento de jovens.

## Identidade do projeto

- Artefato Maven: `dev.lourencogabriel:inat-ead-backend`
- Aplicação Spring: `inat-ead-backend`
- Pacote Java base: `dev.lourencogabriel.inatead`
- Classe de inicialização: `InatEadApplication`
- Banco de dados padrão do ambiente Docker: `inat_ead`

## Escopo atual

O backend contém a base de autenticação e usuários da plataforma, com emissão de JWT, rotação de refresh tokens e suporte a clientes web e mobile. Os domínios educacionais, como cursos, turmas, matrículas, aulas, progresso e certificados, devem ser adicionados conforme a evolução da plataforma.

## Tecnologias

- Java 21
- Spring Boot
- Spring Security e OAuth 2.0 Resource Server
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
| `API_SECURITY_JWT_ISSUER` | Não | Emissor dos tokens; padrão: `inat-ead-backend` |
| `API_SECURITY_REFRESH_COOKIE_SECURE` | Não | Exige HTTPS no cookie de refresh; padrão: `true` |
| `SERVER_PORT` | Não | Porta HTTP; padrão: `8080` |

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
