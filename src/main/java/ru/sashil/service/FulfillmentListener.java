package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import ru.sashil.dto.FulfillmentTask;

@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillmentListener {

    private final InventoryService inventoryService;

    @Value("${fulfillment.queue.name}")
    private String fulfillmentQueueName;

    /**
     * JMS-слушатель для очереди фулфилмента.
     * Сообщения распределяются между узлами кластера.
     * Используется транзакционная сессия для гарантии доставки.
     */
    @JmsListener(destination = "${fulfillment.queue.name}", containerFactory = "jmsListenerContainerFactory")
    public void receiveFulfillmentTask(FulfillmentTask task) {
        log.info("Received fulfillment task from queue: orderNumber={}, orderId={}",
            task.getOrderNumber(), task.getOrderId());

        try {
            inventoryService.executeFulfillment(task);
            log.info("Fulfillment task completed successfully: {}", task.getOrderNumber());
        } catch (Exception e) {
            log.error("Fulfillment task failed: {}", task.getOrderNumber(), e);
            throw new RuntimeException("Fulfillment processing failed", e);
        }
    }
}
