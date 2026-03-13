package ru.sashil.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI Тестирование каталога товаров с помощью Selenium.
 * Предполагается, что приложение запущено на порту 8081.
 */
class CatalogUITest {

    private WebDriver driver;
    private final String baseUrl = "https://gitea.timoapp.tech";

    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Для запуска в CI
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void testCatalogPageContent() {
        driver.get(baseUrl + "/catalog");

        // Проверяем заголовок страницы
        String title = driver.getTitle();
        assertThat(title).contains("Каталог");

        // Проверяем наличие товаров
        List<WebElement> products = driver.findElements(By.className("product-card"));
        assertThat(products).isNotEmpty();

        // Проверяем кнопку добавления в корзину первого товара
        WebElement firstAddToCartBtn = products.get(0).findElement(By.className("btn-primary"));
        assertThat(firstAddToCartBtn.getText()).isEqualTo("В корзину");
    }

    @Test
    void testLoginNavigation() {
        driver.get(baseUrl + "/");
        
        WebElement loginLink = driver.findElement(By.linkText("Вход"));
        loginLink.click();

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(driver.findElement(By.tagName("h2")).getText()).isEqualTo("Вход в личный кабинет");
    }
}
