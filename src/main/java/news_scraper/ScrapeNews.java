package news_scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/*
    Scrape a news site using a google api to search and then iterate through the results
 */
public class ScrapeNews {

    public static void main(String[] args) throws Exception {

        //Using a google api to search the site
        //This allows us to avoid navigation on the site which will be slow and flaky
        //This is a specific design choice to avoid any browser navigation not necessary
        GoogleSearchResults googleSearchResults = rapidAPIGoogleSearch("apnews.com/article", "medical", 2);

        List<ApNewsArticle> articles = new ArrayList<>();

        for (GoogleSearchResult googleSearchResult : googleSearchResults.results) {
            articles.add(new ApNewsArticle(googleSearchResult.link));
        }

        System.out.println(buildHtmlReport(articles));
    }


    private static String buildHtmlReport(List<ApNewsArticle> articles) {
        StringBuffer htmlOutputOfResults = new StringBuffer();

        htmlOutputOfResults.append("<html><body><table border=\"1\"><th>Headline</th><th>Author(s)</th><th>Date</th><th>Link</th><th>Images</th>");

        for (ApNewsArticle article : articles) {
            htmlOutputOfResults.append(article.generateHtmlTableRowWithPageInfo());
        }

        htmlOutputOfResults.append("</table></body></html>");

        return htmlOutputOfResults.toString();
    }

    private static GoogleSearchResults rapidAPIGoogleSearch(String siteToScrape,
                                                            String searchTerm, int numberOfResultsToPull) {

        final String host = "https://rapidapi.p.rapidapi.com/api/v1/search/";
        final String charset = "UTF-8";
        final String x_rapidapi_host = "google-search3.p.rapidapi.com";
        final String x_rapidapi_key = "359e20648fmshcf34f01964377c8p192b74jsn549b053cca66";

        GoogleSearchResults googleSearchResults;

        try {
            String siteQueryEncoded = URLEncoder.encode("q=site:" + siteToScrape + " " + searchTerm, "UTF-8");
            String query = siteQueryEncoded + "&cr=ES&num=" + numberOfResultsToPull;

            String searchApiResponse = Unirest.get(host + query)
                    .header("x-rapidapi-host", x_rapidapi_host)
                    .header("x-rapidapi-key", x_rapidapi_key)
                    .asJson().getBody().toString();

            googleSearchResults = new ObjectMapper().readValue(searchApiResponse, GoogleSearchResults.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return googleSearchResults;
    }

    static class GoogleSearchResults {
        @JsonProperty("image_results")
        public List<Object> image_results;
        @JsonProperty("total")
        public int total;
        @JsonProperty("answers")
        public List<Object> answers;
        @JsonProperty("results")
        public List<GoogleSearchResult> results;
        @JsonProperty("ts")
        public double ts;
    }

    static class GoogleSearchResult {
        @JsonProperty("link")
        public String link;
        @JsonProperty("description")
        public String description;
        @JsonProperty("title")
        public String title;
    }

}
