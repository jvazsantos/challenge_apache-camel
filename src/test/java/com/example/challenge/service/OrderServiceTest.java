// java
package com.example.challenge.service;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.repo.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository repo;

  @InjectMocks
  private OrderService service;

  @Test
  void create_saves_and_returnsCreated() {
	  
	String id= "";  
    NewOrderRequest req = new NewOrderRequest(); // minimal request; mapper will translate
    String customertId = "cust123" ;
    req.setCustomerId(customertId);	
    
    
    NewOrderRequest.Item item1 = new NewOrderRequest.Item();
    item1.setSku("ABC123");
    item1.setQty(2);
    item1.setUnitPrice(49.90);

    NewOrderRequest.Item item2 = new NewOrderRequest.Item();
    item2.setSku("XYZ789");
    item2.setQty(1);
    item2.setUnitPrice(149.90);

    // Adicionando à lista
    List<NewOrderRequest.Item> items = new ArrayList<>();
    items.add(item1);
    items.add(item2);

    // Associando ao objeto principal
    req.setItems(items);
    
    when(repo.save(any(Order.class))).thenAnswer(inv -> {
      Order o = inv.getArgument(0);
      return o;
    });

    Order created = service.create(req);

    assertThat(created).isNotNull();
    assertThat(created.getCustomerId()).isEqualTo(customertId);
    assertThat(created.getItems().size()).isEqualTo(2);
    
    verify(repo).save(any(Order.class));
  }

  @Test
  void updateItems_success_whenStatusIsNew() {
    String id = "";
    Order order = new Order();
    id = order.getId();
    order.setStatus(OrderStatus.NEW);

    UpdateOrderRequest req = new UpdateOrderRequest(); // minimal update; mapper will handle
    UpdateOrderRequest.Item item1 = new UpdateOrderRequest.Item();
    item1.setSku("ABC123");
    item1.setQty(2);
    item1.setUnitPrice(49.90);

    UpdateOrderRequest.Item item2 = new UpdateOrderRequest.Item();
    item2.setSku("XYZ789");
    item2.setQty(1);
    item2.setUnitPrice(149.90);

    // Adicionando à lista
    List<UpdateOrderRequest.Item> items = new ArrayList<>();
    items.add(item1);
    items.add(item2);
    req.setItems(items);
    
    
      
    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(repo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order updated = service.updateItems(id, req);

    assertThat(updated).isNotNull();
    verify(repo).save(updated);
  }

  @Test
  void updateItems_throws_whenOrderNotFound() {
    when(repo.findById("missing")).thenReturn(Optional.empty());
    UpdateOrderRequest req = new UpdateOrderRequest();
    assertThrows(EntityNotFoundException.class, () -> service.updateItems("missing", req));
  }

  @Test
  void updateItems_throws_whenNotNew() {
    
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.PAID);

    when(repo.findById(id)).thenReturn(Optional.of(order));
    UpdateOrderRequest req = new UpdateOrderRequest();

    assertThrows(IllegalStateException.class, () -> service.updateItems(id, req));
    verify(repo, never()).save(any());
  }

  @Test
  void delete_success_whenStatusIsNew() {
    
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.NEW);

    when(repo.findById(id)).thenReturn(Optional.of(order));
    doNothing().when(repo).delete(order);

    service.delete(id);

    verify(repo).delete(order);
  }

  @Test
  void delete_throws_whenOrderNotFound() {
    when(repo.findById("missing")).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.delete("missing"));
  }

  @Test
  void delete_throws_whenNotNew() {
    
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.PAID);

    when(repo.findById(id)).thenReturn(Optional.of(order));

    assertThrows(IllegalStateException.class, () -> service.delete(id));
    verify(repo, never()).delete(any());
  }

  @Test
  void markPaid_success_whenStatusIsNew() {
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.NEW);

    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(repo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    service.markPaid(id);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    verify(repo).save(order);
  }

  @Test
  void markPaid_throws_whenNotNew() {
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.PAID);

    when(repo.findById(id)).thenReturn(Optional.of(order));

    assertThrows(IllegalStateException.class, () -> service.markPaid(id));
    verify(repo, never()).save(any());
  }

  @Test
  void markFailed_success_whenStatusIsNew() {
    Order order = new Order();
    String id = order.getId();
    order.setStatus(OrderStatus.NEW);

    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(repo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    service.markFailed(id);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED_PAYMENT);
    verify(repo).save(order);
  }

  @Test
  void markFailed_throws_whenOrderNotFound() {
    when(repo.findById("missing")).thenReturn(Optional.empty());
    assertThrows(EntityNotFoundException.class, () -> service.markFailed("missing"));
  }
}
