package com.example.challenge.service;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.mapper.OrderMapper;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.repo.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

	private final OrderRepository repo;

	public OrderService(OrderRepository repo) {
		this.repo = repo;
	}

	@Transactional
	public Order create(NewOrderRequest req) {

		Order order = new OrderMapper().toEntity(req);
		return repo.save(order);
	}

	public Optional<Order> get(String id) {
		return repo.findById(id);
	}

	public List<Order> list(Optional<OrderStatus> status) {
		return status.map(repo::findByStatus).orElseGet(repo::findAll);
	}

	private Order findOrder(String id) {
		Order order = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado: " + id));

		if (order.getStatus() != OrderStatus.NEW) {
			throw new IllegalStateException("Não pode processar pedido com status " + order.getStatus());
		}
		return order;

	}

	@Transactional
	public Order updateItems(String id, UpdateOrderRequest req) {
		Order order = findOrder(id);
		order = new OrderMapper().updateEntity(order, req);

		return repo.save(order);
	}

	@Transactional
	public void delete(String id) {
		Order order = findOrder(id);
		repo.delete(order);
	}

	@Transactional
	public void markPaid(String id) {
		Order order = findOrder(id);
		order.setStatus(OrderStatus.PAID);
		repo.save(order);
	}

	@Transactional
	public void markFailed(String id) {
		Order order = findOrder(id);
		order.setStatus(OrderStatus.FAILED_PAYMENT);
		repo.save(order);
	}
}
