package com.example.challenge.integration;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import com.example.challenge.service.OrderService;

import org.apache.camel.LoggingLevel;

/**
 * TODO: Implementar a rota do pagamento:
 * - from("direct:payOrder") com headers orderId e amount
 * - if amount > 1000 -> chamar failureUrl, else -> successUrl
 * - onException(HttpOperationFailedException) com retries/backoff das props
 * - chamar endpoints/beans para marcar PAID/FAILED no serviço
 */
@Component
public class PaymentRoute extends RouteBuilder {
  public static final String DIRECT_PAY = "direct:payOrder";

  private final PaymentProperties props;
  
  private final OrderService orderService;

  public PaymentRoute(PaymentProperties props, OrderService orderService) {
    this.props = props;
    this.orderService = orderService;
  }

  @Override
  public void configure() throws Exception {
	  
	  
	  onException(HttpOperationFailedException.class)
      .maximumRedeliveries(props.getRetry().getMaxRedeliveries())
      .redeliveryDelay(props.getRetry().getRedeliveryDelayMs())
      .useExponentialBackOff()
      .backOffMultiplier(props.getRetry().getBackoffMultiplier())
      .retryAttemptedLogLevel(LoggingLevel.WARN)
      .useOriginalMessage() 
      .handled(true)
      .process(exchange -> {
    	  String orderId = exchange.getProperty("orderId", String.class);
          log.warn("❌ Pagamento falhou para pedido {}", orderId);
          if (orderId != null) {
              orderService.markFailed(orderId);
          } else {
              log.warn("⚠️ Cabecalho orderId em falta.");
          }
      })
      .log("💥 Pagamento marcado como FAILED para pedido com orderId=${header.orderId}");

  // 🚀 Rota principal
	 from("direct:payOrder")
     .routeId("payment-route")
     .log("🔹 Processando pagamento para orderId=${header.orderId}, amount=${header.amount}")

     // 3️⃣ Escolhe a URL conforme o valor
     .process(exchange -> {
    	 String orderId = exchange.getIn().getHeader("orderId", String.class);
         if (orderId == null) {
             throw new IllegalArgumentException("Cabecalho'orderId' obrigatorio");
         }
         exchange.setProperty("orderId", orderId);
    	 
         double amount = exchange.getIn().getHeader("amount", Double.class);
         String url = amount > 1000 ? props.getFailureUrl() : props.getSuccessUrl();
         exchange.setProperty("targetUrl", url);
         exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
         exchange.getIn().setHeader(Exchange.HTTP_URI, url);
    
              })

     .log("➡️ invocando o endpoint de pagamento: ${exchangeProperty.targetUrl}")

     // 4️⃣ Faz a chamada HTTP
     .toD("${exchangeProperty.targetUrl}?throwExceptionOnFailure=true") 

     // 5️⃣ Marca o pedido como pago (se não houve exceção)
     .process(exchange -> {
         String orderId = exchange.getIn().getHeader("orderId", String.class);
         orderService.markPaid(orderId);
         log.info("✅ Pagamento marcado como PAID para pedido {}", orderId);
     })

     .log("✅ Pagamento completado com sucesso para pedido com orderId=${header.orderId}");
  }
}
