package service;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;
import exceptions.BusinessLogicException;
import model.entity.OauthCredentials;
import model.entity.googledrive.FilesResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static constants.AppConstants.CSV_BASE_PATH;
import static constants.AppConstants.MAX_RETRY_COUNT;
import static constants.GssUrls.*;

public class GSSApiService implements ApiService {
    private static final String OAUTH_CREDENTIALS_FORMAT = "{ \"client_id\": \"%s\", \"client_secret\": \"%s\", \"refresh_token\": \"%s\", \"grant_type\": \"%s\" }";

    @Override
    public List<String> getTableNames(OauthCredentials credentials) throws BusinessLogicException {
        String accessToken = this.getAccessToken(credentials);
        List<String> spreadsheetIds = getSpreadsheetIds(accessToken);
        return getTableNamesFromSpreadsheetIds(spreadsheetIds, accessToken);
    }

    private List<String> getSpreadsheetIds(String accessToken) throws BusinessLogicException {
        List<String> spreadsheetIds = new ArrayList<>();

        final HttpGet request = new HttpGet(GOOGLE_DRIVE_FILES_URL);
        request.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            Gson gson = new Gson();
            FilesResponse filesResponse = gson.fromJson(responseString, FilesResponse.class);
            filesResponse.getFiles().forEach(file -> {
                spreadsheetIds.add(file.getId());
            });
        } catch (Exception e) {
            throw new BusinessLogicException("Failed to get spreadsheet IDs", e);
        }

        return spreadsheetIds;
    }

    private List<String> getTableNamesFromSpreadsheetIds(List<String> spreadsheetIds, String accessToken) throws BusinessLogicException {
        List<String> result = new ArrayList<>();

        for (String spreadsheetId : spreadsheetIds) {
            List<String> tableNames = getTableNamesBySheetId(spreadsheetId, accessToken);
            result.addAll(tableNames);
        }

        return result;
    }

    private List<String> getTableNamesBySheetId(String spreadsheetId, String accessToken) throws BusinessLogicException {
        List<String> tableNames = new ArrayList<>();
        String newUrl = String.format(SPREADSHEET_INFO_URL, spreadsheetId);

        int responseCode;
        int retryCount = 0;
        do {
            final HttpGet request = new HttpGet(newUrl);
            request.setHeader("Authorization", "Bearer " + accessToken);

            try (CloseableHttpClient httpClient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpClient.execute(request)) {

                responseCode = response.getCode();

                switch (responseCode) {
                    case 200:
                        List<String> tables = getResponseData(response);
                        tableNames.addAll(tables);
                        return tableNames;
                    case 429:
                        retryCount++;
                        System.out.println("Retry attempt => " + retryCount);
                        Thread.sleep(1000 * retryCount);
                        break;
                    default:
                        throw new BusinessLogicException("Failed to get table names by sheet ID, response code: " + responseCode);
                }
            } catch (Exception e) {
                throw new BusinessLogicException("Failed to get table names by sheet ID", e);
            }
        } while (responseCode == 429 && retryCount < MAX_RETRY_COUNT);

        if (responseCode == 429) {
            throw new BusinessLogicException("Failed to get google spreadsheet after " + constants.AppConstants.MAX_RETRY_COUNT + " retries");
        }
        return tableNames;
    }

    private List<String> getResponseData(CloseableHttpResponse response) throws IOException, ParseException {
        List<String> tableNames = new ArrayList<>();
        String responseString = EntityUtils.toString(response.getEntity());
        JsonObject jsonObject = JsonParser.parseString(responseString).getAsJsonObject();

        String spreadsheetName = jsonObject.getAsJsonObject("properties")
                .get("title").getAsString();

        JsonArray sheets = jsonObject.getAsJsonArray("sheets");
        sheets.forEach(sheet -> {
            tableNames.add(
                    spreadsheetName + "_" + sheet.getAsJsonObject()
                            .getAsJsonObject("properties")
                            .get("title")
                            .getAsString()
            );
        });
        return tableNames;
    }

    public List<String> getColumnNames(OauthCredentials credentials, String tableName) throws BusinessLogicException {
        List<String> columnNames = new ArrayList<>();
        List<JsonObject> tempData = new ArrayList<>();
        Map<String, String> data = new HashMap<>();

        String accessToken = this.getAccessToken(credentials);

        final HttpGet request = new HttpGet(GOOGLE_DRIVE_FILES_URL);
        request.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();

            responseJson.getAsJsonArray("files").forEach(file -> {
                String fileName = file.getAsJsonObject().get("name").getAsString();
                if (tableName.startsWith(fileName)) {
                    tempData.add(file.getAsJsonObject());
                }
            });
        } catch (Exception e) {
            throw new BusinessLogicException("Failed to get spreadsheet IDs", e);
        }

        if (tempData.size() != 1) {
            data = getFilteredSpreadsheetData(tableName, tempData, accessToken);
        }

        String sheetDataGetUrl = String.format(SHEET_COLUMNS_GET_URL, data.get("spreadsheetId"), data.get("sheet"));
        final HttpGet sheetDataGetRequest = new HttpGet(sheetDataGetUrl);
        sheetDataGetRequest.setHeader("Authorization", "Bearer " + accessToken);


        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(sheetDataGetRequest);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();
            Optional<JsonArray> optionalJsonArray = Optional.ofNullable(responseJson.getAsJsonArray("values"));

            if (optionalJsonArray.isPresent()) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<String>>() {
                }.getType();
                columnNames = gson.fromJson(optionalJsonArray.get().get(0), type);
            }
        } catch (Exception e) {
            throw new BusinessLogicException("Failed to sheet data", e);
        }
        return columnNames;
    }

    @Override
    public void outputCsvForAllData(OauthCredentials credentials, String tableName) throws BusinessLogicException {
        String accessToken = this.getAccessToken(credentials);
        Map<String, String> spreadsheetData;

        List<JsonObject> spreadsheetFiles = getAllSpreadsheetFiles(accessToken, tableName);

        if (spreadsheetFiles.size() != 1) {
            spreadsheetData = getFilteredSpreadsheetData(tableName, spreadsheetFiles, accessToken);
        } else {
            spreadsheetData = getFirstSpreadsheetData(tableName, spreadsheetFiles);
        }

        writeCsvForSpreadsheetData(tableName, spreadsheetData, accessToken);
    }

    @Override
    public String getAccessToken(OauthCredentials credentials) throws BusinessLogicException {
        final HttpPost request = new HttpPost(OAUTH_ACCESS_TOKEN_URL);

        String json = String.format(OAUTH_CREDENTIALS_FORMAT,
                credentials.getClientId(), credentials.getClientSecret(), credentials.getRefreshToken(), credentials.getGrantType());

        StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
        request.setEntity(entity);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();

            return responseJson.get("access_token").getAsString();
        } catch (Exception e) {
            throw new BusinessLogicException("Failed to get access token", e);
        }
    }

    private List<JsonObject> getAllSpreadsheetFiles(String accessToken, String tableName) throws BusinessLogicException {
        List<JsonObject> spreadsheetFiles = new ArrayList<>();
        final HttpGet request = new HttpGet(GOOGLE_DRIVE_FILES_URL);
        request.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();

            if (response.getCode() == HttpStatus.SC_OK) {
                Optional<JsonArray> files = Optional.ofNullable(responseJson.getAsJsonArray("files"));
                files.ifPresent(jsonFiles -> jsonFiles.forEach(file -> {
                    Optional<JsonElement> name = Optional.ofNullable(file.getAsJsonObject().get("name"));
                    name.map(JsonElement::getAsString)
                            .filter(tableName::startsWith)
                            .ifPresent(fileName -> spreadsheetFiles.add(file.getAsJsonObject()));
                }));
            } else {
                String errorMsg = responseJson.getAsJsonObject("error").get("message").getAsString();
                throw new BusinessLogicException(errorMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessLogicException("An error occurs. " + e.getMessage());
        }
        return spreadsheetFiles;
    }

    private Map<String, String> getFilteredSpreadsheetData(String tableName, List<JsonObject> spreadsheetFiles, String accessToken) {
        Map<String, String> result = new HashMap<>();
        outerloop:
        for (JsonObject file : spreadsheetFiles) {
            try {
                List<String> tableNames = getTableNamesBySheetId(file.get("id").getAsString(), accessToken);
                for (String name : tableNames) {
                    if (tableName.equals(name)) {
                        result.put("spreadsheetId", file.get("id").getAsString());
                        result.put("sheet", tableName.replaceFirst(file.get("name").getAsString() + "_", ""));
                        break outerloop;
                    }
                }
            } catch (BusinessLogicException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private Map<String, String> getFirstSpreadsheetData(String tableName, List<JsonObject> spreadsheetFiles) {
        Map<String, String> filteredSpreadsheetData = new HashMap<>();
        filteredSpreadsheetData.put("spreadsheetId", spreadsheetFiles.get(0).get("id").getAsString());
        filteredSpreadsheetData.put("sheet", tableName.replaceFirst(spreadsheetFiles.get(0).get("name").getAsString() + "_", ""));

        return filteredSpreadsheetData;
    }

    private static void writeCsvForSpreadsheetData(String tableName, Map<String, String> spreadsheetData, String accessToken) throws BusinessLogicException {
        String sheetDataGetUrl = String.format(SHEET_ALL_DATA_GET_URL, spreadsheetData.get("spreadsheetId"), spreadsheetData.get("sheet"));
        final HttpGet sheetDataGetRequest = new HttpGet(sheetDataGetUrl);
        sheetDataGetRequest.setHeader("Authorization", "Bearer " + accessToken);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(sheetDataGetRequest);
        ) {
            String responseString = EntityUtils.toString(response.getEntity());
            JsonObject responseJson = JsonParser.parseString(responseString).getAsJsonObject();
            Optional<JsonArray> values = Optional.ofNullable(responseJson.getAsJsonArray("values"));

            if (values.isPresent()) {
                Gson gson = new Gson();
                Type type = new TypeToken<String[]>() {
                }.getType();
                try (CSVWriter writer = new CSVWriter(new FileWriter(getCsvOutputFileName(tableName)))) {
                    values.get().forEach(value -> {
                        writer.writeNext(gson.fromJson(value.getAsJsonArray(), type));
                    });
                }
            }
        } catch (Exception e) {
            throw new BusinessLogicException("Failed to sheet data", e);
        }
    }

    private static String getCsvOutputFileName(String tableName) {
        String baseDir = CSV_BASE_PATH;
        String baseName = tableName;
        String extension = ".csv";
        int counter = 0;
        String fileName;

        do {
            fileName = baseName + (counter == 0 ? "" : "(" + counter + ")") + extension;
            counter++;
        } while (Files.exists(Paths.get(baseDir + fileName)));

        return baseDir + fileName;
    }
}