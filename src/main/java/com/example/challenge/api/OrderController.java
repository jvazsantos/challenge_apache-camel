package com.example.challenge.api;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Operações relacionadas com pedidos")
public class OrderController {

	private final OrderService service;
	private final ProducerTemplate template;

	public OrderController(OrderService service, ProducerTemplate template) {
		this.service = service;
		this.template = template;
	}

	@Operation(summary = "Cria um novo pedido")
	@PostMapping
	public ResponseEntity<Order> create(@Valid @RequestBody NewOrderRequest req) {
		Order created = service.create(req);
		return ResponseEntity.created(URI.create("/api/orders/" + created.getId())).body(created);

	}

	@Operation(summary = "Busca um pedido por ID")
	@GetMapping("/{id}")
	public ResponseEntity<Order> get(@PathVariable("id") String id) {

		return service.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@Operation(summary = "Lista pedidos (opcional filtrar por status)")
	@GetMapping
	public ResponseEntity<List<Order>> list(@RequestParam("status") Optional<OrderStatus> status) {
		return service.list(status).stream().findAny().map(l -> ResponseEntity.ok(service.list(status)))
				.orElse(ResponseEntity.noContent().build());
	}

	@Operation(summary = "Atualiza itens de um pedido (apenas se NEW)")
	@PutMapping("/{id}")
	public ResponseEntity<Order> update(@PathVariable("id") String id, @Valid @RequestBody UpdateOrderRequest req) {

		Order updated = service.updateItems(id, req);

		return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
	}

	@Operation(summary = "Exclui um pedido (apenas se NEW)")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") String id) {
		service.delete(id);

		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Processa pagamento do pedido via Camel chamando DummyJSON")
	@PostMapping("/{id}/pay")
	public ResponseEntity<Object> pay(@PathVariable("id") String id) {
		return service.get(id).map(order -> {
			if (order.getStatus() != OrderStatus.NEW)
				return ResponseEntity.status(409).header("Error", "Order must be NEW to process payment").build();

			template.sendBodyAndHeaders("direct:payOrder", null,
					Map.of("orderId", order.getId(), "amount", order.getTotal()));
			return ResponseEntity.accepted().build();
		}).orElseGet(() -> ResponseEntity.notFound().build());

	}
}
