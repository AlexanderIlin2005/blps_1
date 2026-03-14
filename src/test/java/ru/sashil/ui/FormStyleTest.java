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

public class FormStyleTest {

    private WebDriver driver;
    private final String baseUrl = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    void verifyFormStyles() {
        System.out.println("--- CHECKING LOGIN PAGE STYLES ---");
        driver.get(baseUrl + "/login");
        WebElement userInput = driver.findElement(By.name("username"));
        printStyles(userInput, "Login Input");

        System.out.println("\n--- CHECKING CATALOG PAGE STYLES ---");
        driver.get(baseUrl + "/catalog");
        try {
            Thread.sleep(2000);
            WebElement categorySelect = driver.findElement(By.id("categoryFilter"));
            printStyles(categorySelect, "Catalog Select");
            
            WebElement searchInput = driver.findElement(By.id("productSearch"));
            printStyles(searchInput, "Catalog Search");
        } catch (Exception e) {
            System.err.println("Error finding catalog elements: " + e.getMessage());
        }
    }

    private void printStyles(WebElement element, String label) {
        String bg = element.getCssValue("background-color");
        String border = element.getCssValue("border-color");
        String radius = element.getCssValue("border-radius");
        String color = element.getCssValue("color");
        
        System.out.println("[" + label + "]");
        System.out.println("  - Background: " + bg);
        System.out.println("  - Border Color: " + border);
        System.out.println("  - Border Radius: " + radius);
        System.out.println("  - Text Color: " + color);
        

        boolean isGlass = bg.contains("rgba") || bg.equals("transparent");
        System.out.println("  - Visual Check (Glass): " + (isGlass ? "PASS" : "FAIL (Stock?)"));
    }
}
