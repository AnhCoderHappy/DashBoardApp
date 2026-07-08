package com.mdata.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.entity.Order;
import com.mdata.backend.entity.OrderItem;
import com.mdata.backend.repository.OrderItemRepository;
import com.mdata.backend.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class OrderIngestionService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PancakeOrderNormalizer normalizer;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderIngestionService(
            PancakeOrderNormalizer normalizer,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.normalizer = normalizer;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public OrderIngestionResult processPancakePayload(UUID connectionId, String payload, UUID sourceEventId) throws Exception {
        OrderIngestionResult result = new OrderIngestionResult(connectionId);
        JsonNode root = objectMapper.readTree(payload);
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data.isArray()) {
            for (JsonNode orderNode : data) {
                processOne(connectionId, orderNode, result);
            }
        } else if (data.has("orders") && data.get("orders").isArray()) {
            for (JsonNode orderNode : data.get("orders")) {
                processOne(connectionId, orderNode, result);
            }
        } else {
            processOne(connectionId, data, result);
        }
        return result;
    }

    @Transactional
    public OrderIngestionResult processPancakeOrder(UUID connectionId, JsonNode orderNode, UUID sourceEventId) {
        OrderIngestionResult result = new OrderIngestionResult(connectionId);
        processOne(connectionId, orderNode, result);
        return result;
    }

    private void processOne(UUID connectionId, JsonNode orderNode, OrderIngestionResult result) {
        try {
            PancakeOrderNormalizer.NormalizedPancakeOrder normalized = normalizer.normalize(orderNode, connectionId);
            Order order = orderRepository.findByConnectionIdAndPlatformOrderId(connectionId, normalized.platformOrderId())
                    .or(() -> orderRepository.findByPlatformAndPlatformOrderId("pancake", normalized.platformOrderId()))
                    .orElseGet(Order::new);
            LocalDate oldBusinessDate = order.getBusinessDate();

            order.setPlatform("pancake");
            order.setSourceChannel(normalized.sourceChannel());
            order.setPlatformOrderId(normalized.platformOrderId());
            order.setConnectionId(connectionId);
            order.setStatus(normalized.status());
            order.setNormalizedStatus(normalized.normalizedStatus());
            order.setGrossRevenue(normalized.grossRevenue());
            order.setNetRevenue(normalized.netRevenue());
            order.setDiscountAmount(normalized.discountAmount());
            order.setShippingFee(normalized.shippingFee());
            order.setCurrency(normalized.currency());
            order.setCustomerName(normalized.customerName());
            order.setCreatedAtPlatform(normalized.createdAtPlatform());
            order.setUpdatedAtPlatform(normalized.updatedAtPlatform());
            order.setBusinessTime(normalized.businessTime());
            order.setBusinessDate(normalized.businessDate());
            order.setBusinessHour(normalized.businessHour());
            order.setExternalUpdatedAtPlatform(normalized.updatedAtPlatform());
            order.setExternalVersion(normalized.externalVersion());
            order.setRawData(normalized.rawData());
            order.setUpdatedAt(Instant.now());

            Order saved = orderRepository.save(order);
            orderItemRepository.deleteByOrderId(saved.getId());
            for (PancakeOrderNormalizer.NormalizedPancakeOrderItem normalizedItem : normalized.items()) {
                OrderItem item = new OrderItem();
                item.setOrderId(saved.getId());
                item.setPlatformProductId(normalizedItem.platformProductId());
                item.setPlatformSkuId(normalizedItem.platformSkuId());
                item.setSku(normalizedItem.sku());
                item.setProductName(normalizedItem.productName());
                item.setQuantity(normalizedItem.quantity());
                item.setUnitPrice(normalizedItem.unitPrice());
                item.setTotalPrice(normalizedItem.totalPrice());
                item.setRawData(normalizedItem.rawData());
                item.setCreatedAt(Instant.now());
                orderItemRepository.save(item);
            }

            if (oldBusinessDate != null) {
                result.getAffectedDates().add(oldBusinessDate);
            }
            result.getAffectedDates().add(normalized.businessDate());
            result.getConfirmedOrderIds().add(saved.getId());
            result.incrementProcessedCount();
        } catch (Exception ex) {
            result.incrementFailedCount();
            throw ex;
        }
    }
}
