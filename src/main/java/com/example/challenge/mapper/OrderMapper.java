package com.example.challenge.mapper;

import com.example.challenge.domain.*;
import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

	public static double calculateTotal(List<OrderItem> items) {
		return items.stream().mapToDouble(i -> i.getQty() * i.getUnitPrice()).sum();
	}

	public Order toEntity(NewOrderRequest request) {
		Order order = new Order();
		order.setCustomerId(request.getCustomerId());

		List<OrderItem> items = request.getItems().stream().map(i -> {
			OrderItem item = new OrderItem();
			item.setSku(i.getSku());
			item.setQty(i.getQty());
			item.setUnitPrice(i.getUnitPrice());
			item.setOrder(order);
			return item;
		}).toList();

		order.setItems(items);

		double total = calculateTotal(items);

		order.setTotal(total);
		order.setStatus(OrderStatus.NEW);

		return order;
	}

	public Order updateEntity(Order order, UpdateOrderRequest request) {

		List<OrderItem> newItems = request.getItems().stream().map(i -> {
			OrderItem item = new OrderItem();
			item.setSku(i.getSku());
			item.setQty(i.getQty());
			item.setUnitPrice(i.getUnitPrice());
			item.setOrder(order);
			return item;
		}).toList();

		order.getItems().clear();

		order.setItems(newItems);

		double total = calculateTotal(newItems);

		order.setTotal(total);

		return order;
	}

}
