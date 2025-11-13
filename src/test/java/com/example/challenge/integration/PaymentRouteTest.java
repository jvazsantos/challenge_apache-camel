package com.example.challenge.integration;

import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.challenge.service.OrderService;

import java.util.Map;

import static org.mockito.Mockito.*;

class PaymentRouteTest {

	private DefaultCamelContext context;
	private ProducerTemplate template;
	private AutoCloseable mocks;

	@Mock
	private PaymentProperties props;

	@Mock
	private PaymentProperties.Retry retry;

	@Mock
	private OrderService orderService;

	@BeforeEach
	void setup() throws Exception {
		mocks = MockitoAnnotations.openMocks(this);

		when(props.getSuccessUrl()).thenReturn("http://success.example");
		when(props.getFailureUrl()).thenReturn("http://failure.example");
		when(props.getRetry()).thenReturn(retry);
		when(retry.getMaxRedeliveries()).thenReturn(0);
		when(retry.getRedeliveryDelayMs()).thenReturn(1L);
		when(retry.getBackoffMultiplier()).thenReturn(1.0);

		context = new DefaultCamelContext();
		context.addRoutes(new PaymentRoute(props, orderService));
		template = context.createProducerTemplate();

	}

	@AfterEach
	void teardown() throws Exception {
		if (template != null) {
			try {
				template.stop();
			} catch (Exception ignored) {
			}
		}
		if (context != null) {
			try {
				context.stop();
			} catch (Exception ignored) {
			}
		}
		if (mocks != null) {
			mocks.close();
		}
	}

	@Test
	void when_amount_below_threshold_then_calls_success_and_marks_paid() throws Exception {
		RouteDefinition rd = context.getRouteDefinition("payment-route");
		if (rd == null) {
			System.err.println("Route 'payment-route' not found. Available route ids:");
			context.getRouteDefinitions().forEach(r -> System.err.println(" - " + r.getId()));
			throw new IllegalStateException("Route with id 'payment-route' not registered");
		}

		AdviceWith.adviceWith(rd, context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() {
				interceptSendToEndpoint("http*").skipSendToOriginalEndpoint().to("mock:http");
			}
		});

		context.start();
		MockEndpoint http = context.getEndpoint("mock:http", MockEndpoint.class);
		http.expectedMessageCount(1);

		template.start();
		template.sendBodyAndHeaders("direct:payOrder", null, Map.of("orderId", "order-1", "amount", 500.0));

		http.assertIsSatisfied();
		verify(orderService, times(1)).markPaid("order-1");
		verify(orderService, never()).markFailed(anyString());

	}

	@Test
	void when_http_fails_then_marks_failed() throws Exception {
		RouteDefinition rd = context.getRouteDefinition("payment-route");
		if (rd == null) {
			System.err.println("Route 'payment-route' not found. Available route ids:");
			context.getRouteDefinitions().forEach(r -> System.err.println(" - " + r.getId()));
			throw new IllegalStateException("Route with id 'payment-route' not registered");
		}

		AdviceWith.adviceWith(rd, context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() {
				interceptSendToEndpoint("http*").skipSendToOriginalEndpoint().process(exchange -> {
					throw new HttpOperationFailedException("uri", 500, "Server Error", null, null, null);
				});
			}
		});

		context.start();
		template.start();

		template.sendBodyAndHeaders("direct:payOrder", null, Map.of("orderId", "order-2", "amount", 2000.0));
		Thread.sleep(200);

		verify(orderService, times(1)).markFailed("order-2");
		verify(orderService, never()).markPaid(anyString());

	}
}