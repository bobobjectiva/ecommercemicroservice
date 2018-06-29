package com.bdtv.ms.ecom.order.service.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.bdtv.ms.ecom.order.service.data.Product;
import com.bdtv.ms.ecom.order.service.entity.Order;
import com.bdtv.ms.ecom.order.service.entity.OrderEntry;
import com.bdtv.ms.ecom.order.service.repository.OrderEntryRepository;
import com.bdtv.ms.ecom.order.service.repository.OrderRepository;
import com.bdtv.ms.ecom.order.service.service.OrderService;
import com.bdtv.ms.ecom.order.service.service.exception.ResourceNotFoundException;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;

@Service
public class DefaultOrderService implements OrderService {

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderEntryRepository orderEntryRepository;

	@HystrixCommand
	@Override
	public Order createOrder(Order order) {
		order.getOrderEntries().stream().forEach(oe -> oe.setOrder(order));
		return orderRepository.save(order);
	}

	@HystrixCommand
	@Override
	public Order getOrderById(Long orderId) {
		return orderRepository.findById(orderId).orElseThrow(
				() -> new ResourceNotFoundException("Order", "id", orderId));
	}

	@HystrixCommand
	@Override
	public List<Order> getOrders() {
		return orderRepository.findAll();
	}

	@Override
	public void deleteOrderById(Long orderId) {
		Order o = getOrderById(orderId);
		orderRepository.delete(o);
	}

	@HystrixCommand
	@Override
	public Order updateOrder(Long orderId, Order order) {
		Order o = getOrderById(orderId);
		o.setCustomerId(order.getCustomerId());
		o.setName(order.getName());
		o.setSubTotal(order.getSubTotal());
		o.setTax(order.getTax());
		o.setTotalPrice(order.getTotalPrice());
		orderRepository.save(o);
		return o;
	}

	@HystrixCommand(fallbackMethod = "getProductbyIdFallback", threadPoolKey = "productByIdThreadPool", threadPoolProperties = {
			@HystrixProperty(name = "coreSize", value = "30"),
			@HystrixProperty(name = "maxQueueSize", value = "10")})
	@Override
	public Product getProductById(Long productId, String accessToken) {
		// TODO use Feign
		return restTemplate.getForObject(
				"http://microservice.product.service/productapi/product/"
						+ productId + "?access_token=" + accessToken,
				Product.class);
	}

	protected Product getProductbyIdFallback(Long productId, String accessToken) {
		Product p = new Product();
		p.setCode("001");
		p.setName("Fake One");
		p.setDescription("this is a fake product for fallback");
		return p;
	}

	@HystrixCommand
	@Override
	public OrderEntry createOrderEntry(Long orderId, OrderEntry orderEntry) {
		return orderRepository
				.findById(orderId)
				.map(order -> {
					orderEntry.setOrder(order);
					return orderEntryRepository.save(orderEntry);
				})
				.orElseThrow(
						() -> new ResourceNotFoundException("Order", "id",
								orderId));
	}

	@HystrixCommand
	@Override
	public Set<OrderEntry> getOrderEntriesByOrderId(Long orderId) {
		return orderRepository
				.findById(orderId)
				.map(order -> {
					return order.getOrderEntries();
				})
				.orElseThrow(
						() -> new ResourceNotFoundException("Order", "id",
								orderId));
	}

	@HystrixCommand
	@Override
	public OrderEntry updateOrderEntry(Long orderId, Long orderEntryId,
			OrderEntry orderEntry) {
		if (!orderRepository.existsById(orderId)) {
			throw new ResourceNotFoundException("Order", "id", orderId);
		}

		return orderEntryRepository
				.findById(orderEntryId)
				.map(oe -> {
					oe.setCount(orderEntry.getCount());
					oe.setSubTotal(orderEntry.getSubTotal());
					oe.setTax(orderEntry.getTax());
					oe.setTotalPrice(orderEntry.getTotalPrice());
					return orderEntryRepository.save(oe);
				})
				.orElseThrow(
						() -> new ResourceNotFoundException("OrderEntry", "id",
								orderEntryId));
	}

	@HystrixCommand
	@Override
	public void deleteOrderEntry(Long orderId, Long orderEntryId) {
		if (!orderRepository.existsById(orderId)) {
			throw new ResourceNotFoundException("Order", "id", orderId);
		}

		orderEntryRepository
				.findById(orderEntryId)
				.map(oe -> {
					orderEntryRepository.delete(oe);
					return ResponseEntity.ok().build();
				})
				.orElseThrow(
						() -> new ResourceNotFoundException("OrderEntry", "id",
								orderEntryId));
	}

}
