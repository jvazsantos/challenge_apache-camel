# Desafio de Entrevista — Spring Boot + Apache Camel + H2 + DummyJSON

**Você implementará** um mini-serviço de pedidos com CRUD e fluxo de pagamento via **Apache Camel** integrando com a api publica HTTP **DummyJSON**. O projeto já inclui **H2 (memória)** e **JPA**, permitindo **POST/DELETE** reais sem configurar banco externo.

> Este repositório é **apenas ESQUELETO**: entidades e repositório existem, porém **services, controllers e rotas Camel estão em TODO**. Você deve implementar as regras, endpoints, rota e testes. Faz parte do desafio qualquer ajuste para a compilação com sucesso do código, independente do template fornecido.

---

## Requisitos Funcionais
### Integração obrigatória com DummyJSON (Pagamento)
- O endpoint `POST /api/orders/{id}/pay` **deve** disparar uma rota Camel que chama **DummyJSON**.
- Use os **endpoints HTTP**:
  - Sucesso: `GET https://dummyjson.com/http/200` (config: `payment.success-url`)
  - Falha: `GET https://dummyjson.com/http/500` (config: `payment.failure-url`)
- A rota deve aplicar **retry exponencial** para erros HTTP (`onException(HttpOperationFailedException)`) usando os parâmetros do `application.yaml`.
- Ao final:
  - Sucesso → marcar o pedido como **PAID**.
  - Falha após esgotar retries → marcar como **FAILED_PAYMENT**.

- **Pedido (Order)**: `id (UUID)`, `customerId`, `items[] {sku, qty, unitPrice}`, `status` (`NEW`, `PAID`, `FAILED_PAYMENT`, `CANCELLED`), `total` (calculado).
- Endpoints REST a implementar:
  1. `POST /api/orders` — cria pedido (`status=NEW`, calcula `total`, persiste).
  2. `GET /api/orders/{id}` — busca por ID.
  3. `GET /api/orders?status=...` — filtro opcional.
  4. `PUT /api/orders/{id}` — atualiza **itens** (somente se `status=NEW`), recalcula `total`.
  5. `DELETE /api/orders/{id}` — exclui (somente se `status=NEW`).
  6. `POST /api/orders/{id}/pay` — dispara **Camel**:
     - Valor `amount = total` do pedido.
     - Se `amount <= 1000` → chamar `payment.success-url` (200) → marcar `PAID`.
     - Se `amount > 1000` → chamar `payment.failure-url` (500) → aplicar **retry exponencial**. Ao esgotar, marcar `FAILED_PAYMENT`.

## Requisitos Técnicos
- Java 17, Spring Boot 3.x, Apache Camel 4.x.
- Maven (`mvn clean verify` deve passar).
- **H2 em memória + JPA** já configurado em `application.yaml` para viabilizar POST/DELETE.
- **Swagger** via springdoc (anotar controllers).
- **Config** via `application.yaml` (URLs DummyJSON, retries, etc.).
- **Logs** úteis (inclua `orderId` e `exchangeId` no fluxo Camel).

---

## O que já está pronto
- `Order`, `OrderItem` mapeados com JPA.
- `OrderRepository` (Spring Data JPA).
- `application.yaml` com H2 e propriedades `payment.*`.
- `PaymentProperties` e **skeleton** da `PaymentRoute` (Camel).
- `OrderService` e `OrderController` com **métodos TODO**.
- Dependências: Web, Validation, Data JPA, H2, Flyway (opcional), Camel, springdoc.
- Um teste vazio para você substituir.

---

## O que você deve implementar
### 1) Pagamento via Camel (DummyJSON) — **OBRIGATÓRIO**
- Criar a rota `direct:payOrder` recebendo headers `orderId` e `amount`.
- Regras:
  - `amount <= 1000` → chamar `payment.success-url` (espera HTTP 200).
  - `amount > 1000` → chamar `payment.failure-url` (gera HTTP 500 e ativa retry).
- `onException(HttpOperationFailedException)`:
  - `maximumRedeliveries`, `redelivery-delay-ms`, `backoff-multiplier` **devem** vir do `application.yaml`.
- Ao concluir a chamada:
  - Em sucesso → invocar `OrderService.markPaid(orderId)`.
  - Em falha (após retries) → `OrderService.markFailed(orderId)`.

### 2) Lógica de domínio (service)

1. **Lógica de domínio (service)**
   - Mapear DTO → entidades.
   - Calcular `total` (qty × unitPrice) e setar `status=NEW` na criação.
   - Regras: atualizar/excluir apenas se `status=NEW`.
   - `markPaid` e `markFailed` para alteração de status.

2. **API REST (controller)**
   - Implementar endpoints listados com códigos HTTP adequados.
   - Anotar com Swagger/OpenAPI (`@Operation`, schemas etc.).

3. **Rota Camel (integration)**
   - `direct:payOrder` recebendo `orderId` e `amount` em headers.
   - `onException(HttpOperationFailedException)` com `max-redeliveries`, `redelivery-delay-ms`, `backoff-multiplier` das props.
   - Chamar `payment.success-url` ou `payment.failure-url` conforme regra.
   - Ao final, invocar **service.markPaid** ou **service.markFailed** (via bean ou `direct:` auxiliar).

4. **Testes Unitários**
   - **Camel (Pagamento)**: mockar a chamada HTTP da rota (`AdviceWith`/`mock:`) cobrindo sucesso e falha com retries.
   - **Service/REST**: criar cenários de criação, atualização condicional, exclusão condicional e `/pay`.

---

## (Extra - Não Obrigatório) Enriquecimento de domínio com DummyJSON
Para exercitar a lógica extra e integração real além da simulação de 200/500 com os endpoints http/200 e http/500, implemente **validação e precificação por SKU** usando **DummyJSON Products**:

1. **Validação de SKU/Produto:**
   - Ao criar/atualizar um pedido, para cada item `sku` (p. ex. `id` numérico), consulte `GET https://dummyjson.com/products/{sku}`.
   - Se o produto **não existir** → rejeite o item e retorne **400** com mensagem clara.
2. **Preço autoritativo da API:**
   - Ignore `unitPrice` informado pelo cliente e **use o preço retornado pelo DummyJSON** (ex.: `price`) para o cálculo do `total`.
3. **Cache simples:**
   - Implemente cache em memória (ex.: `ConcurrentHashMap` ou `@Cacheable`) para evitar refetch do mesmo SKU no mesmo run.
4. **Testes adicionais:**
   - Mocke a integração de produtos para cobrir: produto existente, inexistente e erro de rede.
5. **Configurações:**
   - Deixe a URL base `dummyjson.base-url` no `application.yaml` e permita ajuste fácil de timeouts.

> Dica: mantenha este enriquecimento **independente** do fluxo de pagamento. O `/pay` continua usando os endpoints simulando os retornos (`/http/200` e `/http/500`) para testar resiliência e retries.


   - Serviço: cálculo do total e regras de status.
   - Camel: sucesso e falha com retries (mockar HTTP via `AdviceWith` e `mock:`).
   - REST: criação, atualização condicional, exclusão condicional, `/pay` (feliz e erro).

6. **Bônus:**
   - Métricas/health (Actuator).
   - Dockerfile/Compose.
   - Enriquecer logs consultando `GET /products/{id}` na DummyJSON.
---

## Como rodar
```bash
mvn clean spring-boot:run
# ou
mvn clean verify && java -jar target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar
```
- Swagger: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (JDBC: `jdbc:h2:mem:ordersdb`, user `sa`, pass `sa`).

---

## Dicas
- Para testar retries, mantenha `payment.failure-url: https://dummyjson.com/http/500`.
- Para logs, inclua `exchange.getExchangeId()` e `orderId`.
- Se quiser usar Flyway, adicione migrations em `src/main/resources/db/migration` (opcional).

---

## Entrega
- Repositório público com código atualizado conforme instruções do challenge.
- Código compilando, exemplo: `mvn clean package`.
- Não suba a pasta `target/`.
- **Não suba nada no repositório de origem do desafio**

---

**Hora de compilar talento em código. Boa sorte!**
