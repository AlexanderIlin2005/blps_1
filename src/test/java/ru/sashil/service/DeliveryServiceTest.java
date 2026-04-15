package ru.sashil.service;

import jakarta.jms.TextMessage;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.Interaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sashil.jca.StringRecord;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.repository.OrderRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private OrderService orderService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ConnectionFactory accountingConnectionFactory;
    @Mock
    private Connection connection;
    @Mock
    private Interaction interaction;
    @Mock
    private TextMessage message;

    @Test
    void sendsDeliveryEmailOnlyAfterAccountingSucceeds() throws Exception {
        DeliveryService deliveryService = spy(new DeliveryService(
            orderService,
            notificationService,
            orderRepository,
            transactionTemplate,
            accountingConnectionFactory
        ));

        Order order = buildCourierOrder();
        prepareSuccessfulDelivery(deliveryService, order);

        serviceOnDeliveryMessage(deliveryService);

        InOrder inOrder = inOrder(interaction, notificationService);
        inOrder.verify(interaction).execute(isNull(), any(StringRecord.class), any(StringRecord.class));
        inOrder.verify(notificationService).sendDeliveryNotification(order);
        verify(orderService).completeDelivery(order.getOrderNumber());
    }

    @Test
    void doesNotSendDeliveryEmailWhenAccountingRejectsPayload() throws Exception {
        DeliveryService deliveryService = spy(new DeliveryService(
            orderService,
            notificationService,
            orderRepository,
            transactionTemplate,
            accountingConnectionFactory
        ));

        Order order = buildCourierOrder();
        prepareTransactionTemplate();
        doNothing().when(deliveryService).waitForStageTransition();
        when(message.getText()).thenReturn("{\"orderNumber\":\"ORD-DELIVERY-1\",\"total\":14990.0}");
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(accountingConnectionFactory.getConnection()).thenReturn(connection);
        when(connection.createInteraction()).thenReturn(interaction);
        doAnswer(invocation -> {
            StringRecord output = invocation.getArgument(2);
            output.setPayload("ERROR: ledger write failed");
            return true;
        }).when(interaction).execute(isNull(), any(StringRecord.class), any(StringRecord.class));

        assertThrows(RuntimeException.class, () -> serviceOnDeliveryMessage(deliveryService));

        verify(notificationService, never()).sendDeliveryNotification(any());
    }

    private void prepareSuccessfulDelivery(DeliveryService deliveryService, Order order) throws Exception {
        prepareTransactionTemplate();
        doNothing().when(deliveryService).waitForStageTransition();
        when(message.getText()).thenReturn("{\"orderNumber\":\"ORD-DELIVERY-1\",\"total\":14990.0}");
        when(orderRepository.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(accountingConnectionFactory.getConnection()).thenReturn(connection);
        when(connection.createInteraction()).thenReturn(interaction);
        doAnswer(invocation -> {
            StringRecord output = invocation.getArgument(2);
            output.setPayload("ACK: Processed");
            return true;
        }).when(interaction).execute(isNull(), any(StringRecord.class), any(StringRecord.class));
    }

    private void prepareTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            TransactionStatus status = mock(TransactionStatus.class);
            return callback.doInTransaction(status);
        });
    }

    private void serviceOnDeliveryMessage(DeliveryService deliveryService) {
        deliveryService.onDeliveryMessage(message);
    }

    private Order buildCourierOrder() {
        Order order = new Order();
        order.setOrderNumber("ORD-DELIVERY-1");
        order.setDeliveryType(DeliveryType.COURIER);
        order.setDeliveryAddress("Moscow, Test st. 1");
        order.setCustomerEmail("buyer@example.com");
        return order;
    }
}
