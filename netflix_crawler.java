import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;


public class netflix_crawler {



    private static void crawlNetflix() {
        System.setProperty("webdriver.chrome.driver", "/opt/homebrew/bin/chromedriver");
        // ChromeDriver class.
        WebDriver driver = new ChromeDriver();

        //get subscription plan data From Netflix Plan Page
        getNetflixSubscriptionPlanData(driver);

        //get movie and TV Series data From Netflix
        getNetflixMovieAndTVSeries(driver);

        // close selenium driver
        driver.close();

        // quit selenium driver
        driver.quit();
    }

    private static void getNetflixMovieAndTVSeries(WebDriver driver) {
        int max = 100;
        List<Object[]> data = new ArrayList<>();
        data.add(new Object[]{"Type", "Name", "Description", "Genre", "Release Date", "Season", "Cast", "Platform", "Link" });
        String name, description, genre, releaseDate, season, cast;

        driver.navigate().to("https://www.netflix.com/ca/browse/genre/83");
        List<WebElement> linkElements = driver.findElements(By.tagName("a"));
        // Create a list to store the Netflix links
        List<String> netflixTVShowLinks = new ArrayList<>();
        int count = 0;

        for (WebElement element : linkElements) {
            String href = element.getAttribute("href");
            if (href != null && href.startsWith("https://www.netflix.com/ca/title/")) {
                netflixTVShowLinks.add(href);
            }
            if (count > max) {
                break;
            }
            count++;
        }

        // Visit each Netflix link sequentially
        for (String url : netflixTVShowLinks) {
            System.out.println("Visiting: " + url);
            driver.get(url);
            WebElement nameE = driver.findElement(By.xpath("//h1[@class='title-title']"));
            name = nameE.getText();
            try {
                WebElement descriptionE = driver.findElement(By.xpath("//div[@class='title-info-synopsis']"));
                description = descriptionE.getText();
            } catch (NoSuchElementException e) {
                description = "";
            }
            try {
                WebElement genreE = driver.findElement(By.xpath("//a[@class='title-info-metadata-item item-genre']"));
                genre = genreE.getText();
            } catch (NoSuchElementException e) {
                genre = "";
            }
            try {
                WebElement releaseDateE = driver.findElement(By.xpath("//span[@class='title-info-metadata-item item-year']"));
                releaseDate = releaseDateE.getText();
                if (!isValidYear(releaseDate)) {
                    System.out.println(releaseDate + " is NOT a valid year format.");
                }
            } catch (NoSuchElementException e) {
                releaseDate = "";
            }
            try {
                WebElement seasonE = driver.findElement(By.xpath("//span[@class='test_dur_str']"));
                season = seasonE.getText();
            } catch (NoSuchElementException e) {
                season = "";
            }
            try {
                WebElement castE = driver.findElement(By.xpath("//span[@class='title-data-info-item-list']"));
                cast = castE.getText();
            } catch (NoSuchElementException e) {
                cast = "";
            }

            data.add(new Object[]{"TV Show", "\"" + name + "\"", "\"" + description + "\"", genre, releaseDate, season, "\"" + cast + "\"", "Netflix", driver.getCurrentUrl()});
        }

        // MOVIES
        driver.navigate().to("https://www.netflix.com/ca/browse/genre/34399");
        linkElements = driver.findElements(By.tagName("a"));
        // Create a list to store the Netflix links
        List<String> netflixMovieLinks = new ArrayList<>();
        count = 0;
        for (WebElement element : linkElements) {
            String href = element.getAttribute("href");
            if (href != null && href.startsWith("https://www.netflix.com/ca/title/")) {
                netflixMovieLinks.add(href);
            }
            if (count > max) {
                break;
            }
            count++;
        }


        for (String url : netflixMovieLinks) {
            System.out.println("Visiting: " + url);
            driver.get(url);
            WebElement nameE = driver.findElement(By.xpath("//h1[@class='title-title']"));
            name = nameE.getText();
            try {
                WebElement descriptionE = driver.findElement(By.xpath("//div[@class='title-info-synopsis']"));
                description = descriptionE.getText();
            } catch (NoSuchElementException e) {
                description = "";
            }
            try {
                WebElement genreE = driver.findElement(By.xpath("//a[@class='title-info-metadata-item item-genre']"));
                genre = genreE.getText();
            } catch (NoSuchElementException e) {
                genre = "";
            }
            try {
                WebElement releaseDateE = driver.findElement(By.xpath("//span[@class='title-info-metadata-item item-year']"));
                releaseDate = releaseDateE.getText();
            } catch (NoSuchElementException e) {
                releaseDate = "";
            }
            try {
                WebElement castE = driver.findElement(By.xpath("//span[@class='title-data-info-item-list']"));
                cast = castE.getText();
            } catch (NoSuchElementException e) {
                cast = "";
            }
            data.add(new Object[]{"Movie", "\"" + name + "\"", "\"" + description + "\"", genre, releaseDate, "-",  "\"" + cast + "\"", "Netflix", driver.getCurrentUrl()});
        }


        String allData = "";
        for (Object[] item : data) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < item.length; i++) {
                sb.append(item[i]);
                // Append comma only if it's not the last element
                if (i != item.length - 1) {
                    sb.append(",");
                }
            }
            allData += sb.toString() + "\n";
        }
        //write netflix movies and TV series data
        writeFile(allData, "netflixTVShowsAndMovies.csv");
    }

    private static void getNetflixSubscriptionPlanData(WebDriver driver) {
        // Launch Website
        driver.navigate().to("https://www.netflix.com/");
        List<String> header = new ArrayList<>();
        List<String> data = new ArrayList<>();

        // click "Learn More" button on the homepage
        WebElement button = driver.findElement(By.xpath("//button[text()='Learn More']"));
        button.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // click "Explore All Plans" button to access plan page
        WebElement newElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//button[text()='Explore All Plans']")));
        newElement.click();


        // getting element which data-uia=form-plan-selection
        WebElement newElement2 = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@data-uia='form-plan-selection']")));
        List<WebElement> elements = newElement2.findElements(By.xpath("./*"));

        //getting plan data from the element
        for (int i = 0; i < elements.size(); i++) {
            Scanner scanner = new Scanner(elements.get(i).getText());
            int row = 0;
            while (scanner.hasNextLine()) {
                // process the line
                String line = scanner.nextLine();
                if (i == 2 && row == 0) {
                    header.add("Plan Name");
                    data.add(line.replace(',', ' '));
                } else if (i == 2 && row == 1) {
                    header.add("Quality");
                    data.add(line.replace(',', ' '));
                } else if (i == 2 && row % 2 == 0) {
                    header.add(line);
                } else if (row == 7 && (i == 0 || i == 1)) {
                    data.add(line.replace(',', ' '));
                    data.add("-");
                } else if (row == 0 || row % 2 != 0) {
                    data.add(line.replace(',', ' '));
                }
                if (!scanner.hasNextLine()) {
                    data.add(driver.getCurrentUrl());
                    data.add("Netflix");
                }
                row++;
            }
            scanner.close();
        }
        header.add("Link");
        header.add("Platform");
        // converting header info to csv format
        String headerStr = String.join(",", header);
        headerStr = headerStr + "\n";
        String dataStr = new String();

        // converting plan data to csv format
        for (int i = 0; i < data.size(); i++) {
            if ((i + 1) % header.size() == 0) {
                dataStr += data.get(i) + "\n";
            } else {
                dataStr += data.get(i) + ",";
            }
        }
        String planTemp = headerStr + dataStr;
        String plan = "";

        Scanner scanner = new Scanner(planTemp);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] tokens = line.split(",");
            List<String> newTokens = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                if (i == 1 || i == 3 || i == 5 || i == 8 || i == 9) {
                    continue;
                }
                if (i == 2 && !tokens[i].equals("Monthly price")) {
                    String price = tokens[i];
                    if (!isValidPrice(price)) {
                        System.out.println(price + " is not a valid price format.");
                    }
                }
                newTokens.add(tokens[i]);
            }
            plan += String.join(",", newTokens) + "\n";
        }
        //write netflix subscription plan data
        writeFile(plan, "netflixSubscriptionPlan.csv");
    }

    private static void writeFile(String plan, String fileName) {
        File file = new File(fileName);
        FileWriter fr = null;
        try {
            fr = new FileWriter(file);
            fr.write(plan);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //close resources
            try {
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isValidYear(String year) {
        String YEAR_REGEX = "^\\d{4}$";
        return year.matches(YEAR_REGEX);
    }

    private static boolean isValidPrice(String price) {
        String PRICE_REGEX = "^\\$?\\d+(\\.\\d{2})?$";
        return price.matches(PRICE_REGEX);
    }
}
