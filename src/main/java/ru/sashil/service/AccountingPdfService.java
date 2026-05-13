package ru.sashil.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import ru.sashil.model.DeliveryType;
import ru.sashil.model.Order;
import ru.sashil.model.OrderItem;
import ru.sashil.util.RussianMoneyFormatter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingPdfService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<String> FONT_CANDIDATES = List.of(
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf"
    );

    private final SpringTemplateEngine templateEngine;

    @Value("${accounting.company.name:ElectroGlass}")
    private String companyName;
    @Value("${accounting.company.inn:7701234567}")
    private String companyInn;
    @Value("${accounting.company.ogrn:1027700000000}")
    private String companyOgrn;
    @Value("${accounting.company.legal-address:Moscow, Tverskaya 1}")
    private String companyAddress;
    @Value("${accounting.company.phone:+7 (495) 000-00-00}")
    private String companyPhone;
    @Value("${accounting.company.email:docs@electroglass.local}")
    private String companyEmail;
    @Value("${accounting.company.manager:Менеджер магазина}")
    private String companyManager;

    public byte[] renderShippingPdf(Order order) {
        Context context = new Context();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("orderNumber", order.getOrderNumber());
        variables.put("orderDate", formatDateTime(order.getCreatedAt()));
        variables.put("shippingDate", formatDateTime(resolveShippingDate(order)));
        variables.put("deliveryMethod", getDeliveryMethod(order));
        variables.put("customerName", safe(order.getCustomerName()));
        variables.put("customerPhone", safe(order.getCustomerPhone()));
        variables.put("deliveryAddress", safe(resolveCustomerAddress(order)));
        variables.put("customerComment", "Комментарий не указан");
        variables.put("items", buildShippingItems(order));
        variables.put("totalItemsCount", order.getItems().stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum());
        context.setVariables(variables);
        return render("pdf/shipping-document", context);
    }

    public byte[] renderWaybillPdf(Order order) {
        double deliveryPrice = 0.0;
        Context context = new Context();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("invoiceNumber", "TN-" + order.getOrderNumber());
        variables.put("invoiceDate", formatDate(resolveShippingDate(order)));
        variables.put("orderNumber", order.getOrderNumber());
        variables.put("orderDate", formatDate(order.getCreatedAt()));
        variables.put("shopCompanyName", companyName);
        variables.put("shopInn", companyInn);
        variables.put("shopOgrn", companyOgrn);
        variables.put("shopLegalAddress", companyAddress);
        variables.put("shopPhone", companyPhone);
        variables.put("shopEmail", companyEmail);
        variables.put("customerName", safe(order.getCustomerName()));
        variables.put("customerPhone", safe(order.getCustomerPhone()));
        variables.put("customerAddress", safe(resolveCustomerAddress(order)));
        variables.put("items", buildWaybillItems(order));
        variables.put("showDeliveryRow", deliveryPrice > 0.0);
        variables.put("deliveryPrice", formatMoney(deliveryPrice));
        variables.put("totalOrderSum", formatMoney(order.getTotal()));
        variables.put("vatSum", "Без НДС");
        variables.put("totalLinesCount", order.getItems().size());
        variables.put("totalOrderSumInWords", RussianMoneyFormatter.formatRublesAndKopecks(order.getTotal()));
        variables.put("shopManager", companyManager);
        context.setVariables(variables);
        return render("pdf/waybill-document", context);
    }

    private byte[] render(String templateName, Context context) {
        try {
            String html = templateEngine.process(templateName, context);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.useFont(resolveFont(), "DejaVu Sans");
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render PDF template " + templateName, e);
        }
    }

    private File resolveFont() {
        return FONT_CANDIDATES.stream()
            .map(File::new)
            .filter(File::exists)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("DejaVu Sans font is required for Cyrillic PDF rendering"));
    }

    private List<Map<String, Object>> buildShippingItems(Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (OrderItem item : order.getItems()) {
            items.add(Map.of(
                "index", index++,
                "sku", safe(item.getProductId()),
                "name", safe(item.getProductName()),
                "qty", item.getQuantity() == null ? 0 : item.getQuantity()
            ));
        }
        return items;
    }

    private List<Map<String, Object>> buildWaybillItems(Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (OrderItem item : order.getItems()) {
            double total = item.getTotal() == null ? 0.0 : item.getTotal();
            items.add(Map.of(
                "index", index++,
                "name", safe(item.getProductName()),
                "qty", item.getQuantity() == null ? 0 : item.getQuantity(),
                "price", formatMoney(item.getPrice()),
                "rowTotal", formatMoney(total)
            ));
        }
        return items;
    }

    private LocalDateTime resolveShippingDate(Order order) {
        if (order.getShippedAt() != null) {
            return order.getShippedAt();
        }
        return LocalDateTime.now();
    }

    private String resolveCustomerAddress(Order order) {
        if (order.getDeliveryType() == DeliveryType.COURIER) {
            return order.getDeliveryAddress();
        }
        return order.getPickupPointAddress();
    }

    private String getDeliveryMethod(Order order) {
        return order.getDeliveryType() == DeliveryType.COURIER ? "Курьерская доставка" : "Самовывоз";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "Не указано" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "Не указано" : DATE_FORMATTER.format(value);
    }

    private String formatMoney(Double value) {
        return String.format("%.2f", value == null ? 0.0 : value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Не указано" : value;
    }
}
