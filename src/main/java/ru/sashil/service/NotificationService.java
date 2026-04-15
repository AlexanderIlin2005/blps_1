package ru.sashil.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import ru.sashil.model.Order;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendOrderConfirmation(Order order) {
        log.info("Sending order confirmation to {} for order {}", order.getCustomerEmail(), order.getOrderNumber());
    }

    public void sendTrackingInfo(Order order) {
        log.info("Sending tracking info to {} for order {}", order.getCustomerEmail(), order.getOrderNumber());
    }

    public void sendDeliveryNotification(Order order) {
        String recipient = order.getCustomerEmail();
        if (recipient == null || recipient.trim().isEmpty()) {
            recipient = order.getCustomerPhone();
        }

        log.info("Sending email delivery notification to {} for order {}", recipient, order.getOrderNumber());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("order", order);
            
            String htmlContent = templateEngine.process("email-delivery", context);

            helper.setFrom("electroglass@timoapp.tech");
            helper.setTo(recipient);
            helper.setSubject("Заказ " + order.getOrderNumber() + " доставлен!");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", recipient);
        } catch (Exception e) {
            log.error("Failed to send email to {}", recipient, e);
        }
    }

    public void sendOrderCancelledNotification(Order order) {
        log.info("Order {} has been cancelled", order.getOrderNumber());
    }

    public void sendOutOfStockNotification(Order order) {
        log.info("Some items in order {} are out of stock.", order.getOrderNumber());
    }
}
