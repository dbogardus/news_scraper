package news_scraper;

import com.google.common.collect.ImmutableSet;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
    Class to browse to an AP News Article page, scrape data, and format the data for output
    dbogardus 1/8/2021
 */
class ApNewsArticle {

    static{
        System.setProperty("webdriver.chrome.driver", "/chromedriver");
    }

    ApNewsArticle(String apNewsArticleUrl) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--log-level=3");
        WebDriver driver = new ChromeDriver(options);

        link = apNewsArticleUrl;
        try {
            System.out.println("Scraping page now : " + link);
            driver.get(link);
            waitUntilPageLoaded(driver);
            this.headline = driver.getTitle();
            this.author = findAuthor(driver);
            this.dateTimePosted = findDateTimePosted(driver);
            this.imageLinks = findImageLinks(driver);
        }
        catch (WebDriverException e){
            //something bad happening talking to the page, we'll just leave the object partially populated
            System.out.println("Problem scraping page [" + link + "], got error [" + e.getMessage() + "]");
        }
        finally {
            driver.quit();
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Variables, getters, setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private String link;
    private String headline;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private String author;

    private String findAuthor(WebDriver driver){
        try{
            return findElement(driver, By.cssSelector("[class*='byline']")).getText();
        }
        catch(NoSuchElementException e){
            return "[AUTHOR NOT FOUND]";
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private String dateTimePosted;
    private String findDateTimePosted(WebDriver driver){
        try{
            return findElement(driver, By.cssSelector("span[data-key='timestamp']")).getAttribute("data-source");
        }
        catch(NoSuchElementException e){
            return "[DATE NOT FOUND]";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private Set<String> imageLinks;

    public ImmutableSet<String> getImageLinks() {
        return ImmutableSet.copyOf(imageLinks);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////



    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Utilities
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private Set<String> findImageLinks(WebDriver driver) {
        Set<String> imageLinks = new HashSet();

        //On some articles there are many images that are accessed by clicking an arrow on the first image
        By imageAdvanceArrowBy = By.cssSelector("[class*='gallery-arrow']");


        if(findElements(driver, imageAdvanceArrowBy).size() > 0){
            //If we're here, then we've got multiple images to scroll through, click on the image to bring up the modal

            //Flaky Fix - The click on the image gets intercepted on some pages, just clicking on the spot is fine
            WebElement imageGalleryArrow = findElement(driver, imageAdvanceArrowBy);
            Actions builder = new Actions(driver);
            builder.moveToElement(imageGalleryArrow, 0, 0).click().build().perform();

            imageLinks.addAll(findAllImagesLinksOnPage(driver));

            //Cycle through the images in the modal, but limit the number of images we'll grab to prevent infinite loop
            imageAdvanceArrowBy = By.cssSelector("svg[class*='right-']"); // Arrow changes after entering image modal
            int currentImageCylceClicks = 0;
            int maxImageCycleClicks = 100;
            while(findElements(driver, imageAdvanceArrowBy).size() > 0 && currentImageCylceClicks <= maxImageCycleClicks){
                findElement(driver, imageAdvanceArrowBy).click();
                imageLinks.addAll(findAllImagesLinksOnPage(
                        driver.findElement(By.cssSelector("div[class*='imagePlaceholder']"))));
            }
        }else{
            //There was no arrow to open multi-image modal, just grab the images on the main page.
            scrollToBottomOfPageSlowlySoThatImagesWillLoad(driver);
            imageLinks.addAll(findAllImagesLinksOnPage(driver));
        }

        return imageLinks;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void scrollToBottomOfPageSlowlySoThatImagesWillLoad(WebDriver driver){
        WebElement body = findElement(driver, By.tagName("body"));
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        for(int i = 0 ; i < 50 ; i++){
            closeAnyPopups(driver);
            Long beforeScrollPosition = (Long) executor.executeScript("return window.pageYOffset;");
            body.sendKeys(Keys.PAGE_DOWN);
            sleep(500);
            Long afterScrollPosition = (Long) executor.executeScript("return window.pageYOffset;");
            if(beforeScrollPosition >= afterScrollPosition){
                //We've reached the bottom
                break;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private Set<String> findAllImagesLinksOnPage(SearchContext searchContext){
        Set<String> imageLinks = new HashSet();

        //Only grab the press photos which are all hosted on google
        List<WebElement> photos = searchContext.findElements(By.cssSelector("img[src*='googleapis']"));

        for (WebElement photo : photos) {
            try {
                String imageSource = photo.getAttribute("src");
                imageLinks.add(imageSource);
            }
            catch (StaleElementReferenceException e){
                //Don't worry about the reference being stale, nothing we can do just move on
            }
        }

        return imageLinks;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Wait until the height of the page stabilizes
    private void waitUntilPageLoaded(WebDriver driver){

        int waitCounterInSeconds = 0;
        int maxSecondsToWait = 16;
        int sleepIntervalInSeconds = 4;
        int lastPageHeight = 0;
        while(getPageHeight(driver) != lastPageHeight && waitCounterInSeconds <= maxSecondsToWait ){
            lastPageHeight = getPageHeight(driver);
            waitCounterInSeconds+=sleepIntervalInSeconds;
            sleep(sleepIntervalInSeconds * 1000);
        }
        closeAnyPopups(driver);
    }

    private int getPageHeight(WebDriver driver) {
        return driver.manage().window().getSize().height;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Do findElements through this to close email nag popup on page if it appears
    private WebElement findElement(WebDriver driver, By by){
        closeAnyPopups(driver);
        return driver.findElement(by);
    }

    private List<WebElement> findElements(WebDriver driver, By by){
        closeAnyPopups(driver);
        return driver.findElements(by);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void closeAnyPopups(WebDriver driver){
        By overlayCloseButton = By.cssSelector("[class='sailthru-overlay-close']");

        if (driver.findElements(overlayCloseButton).size() > 0) {
            driver.findElement(overlayCloseButton).click();
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void sleep(int ms){
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public String generateHtmlTableRowWithPageInfo(){
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<tr>");
        stringBuffer.append("<td><a href=\""+this.link+"\">"+this.headline+"</a></td>");
        stringBuffer.append("<td>"+this.author+"</td>");
        stringBuffer.append("<td>"+this.dateTimePosted+"</td>");
        stringBuffer.append("<td><a href=\""+this.link+"\">"+this.link+"</a></td>");
        stringBuffer.append("<td>");
        for(String imageLink : imageLinks){
            stringBuffer.append("<a href=\""+imageLink+"\">"+imageLink+"</a><br>");
        }
        stringBuffer.append("</td>");
        stringBuffer.append("<tr>");
        return stringBuffer.toString();
    }
}
