// `src/test/java/com/example/challenge/api/OrderControllerTest.java`
package com.example.challenge.api;

import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.apache.camel.ProducerTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockBean
	private OrderService service;

	@MockBean
	private ProducerTemplate template;

	@Test
	void get_returnsOrder_whenFound() throws Exception {
		Order order = new Order();
		String id = order.getId();
		order.setStatus(OrderStatus.NEW);

		when(service.get(id)).thenReturn(Optional.of(order));

		mvc.perform(get("/api/orders/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void get_returnsNotFound_whenMissing() throws Exception {
		when(service.get("1")).thenReturn(Optional.empty());

		mvc.perform(get("/api/orders/1")).andExpect(status().isNotFound());
	}

	@Test
	void list_returnsNoContent_whenEmpty() throws Exception {
		when(service.list(any())).thenReturn(Collections.emptyList());

		mvc.perform(get("/api/orders")).andExpect(status().isNoContent());
	}

	@Test
	void pay_accepts_and_sendsCamelMessage_whenOrderIsNew() throws Exception {
		Order order = new Order();
		String id = order.getId();
		order.setStatus(OrderStatus.NEW);
		order.setTotal(150.0);

		when(service.get(id)).thenReturn(Optional.of(order));
		doNothing().when(template).sendBodyAndHeaders(eq("direct:payOrder"), isNull(), anyMap());

		mvc.perform(post("/api/orders/" + id + "/pay")).andExpect(status().isAccepted());

		verify(template).sendBodyAndHeaders(eq("direct:payOrder"), isNull(),
				argThat(map -> id.equals(map.get("orderId")) && Double.valueOf(map.get("amount").toString()) == 150.0));
	}

	@Test
	void pay_returnsConflict_whenOrderNotNew() throws Exception {
		Order order = new Order();
		String id = order.getId();
		order.setStatus(OrderStatus.PAID);

		when(service.get(id)).thenReturn(Optional.of(order));

		mvc.perform(post("/api/orders/" + id + "/pay")).andExpect(status().isConflict())
				.andExpect(header().string("Error", "Order must be NEW to process payment"));
	}
}
