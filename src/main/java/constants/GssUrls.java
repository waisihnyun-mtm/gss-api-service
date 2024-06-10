package constants;

public class GssUrls {
    public static final String OAUTH_ACCESS_TOKEN_URL = "https://accounts.google.com/o/oauth2/token";
    public static final String GOOGLE_DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&corpora=user&fields=nextPageToken%2C%20files%28id%2Cname%2CmimeType%20%29&includeItemsFromAllDrives=false&q=trashed%3Dfalse%20AND%20mimeType%3D%27application%2Fvnd.google-apps.spreadsheet%27&pageSize=1000";
    public static final String SPREADSHEET_INFO_URL = "https://sheets.googleapis.com/v4/spreadsheets/%s?includeGridData=false";
    public static final String SHEET_COLUMNS_GET_URL = "https://sheets.googleapis.com/v4/spreadsheets/%s/values/'%s'!A1%%3AZZZ51";
    public static final String SHEET_ALL_DATA_GET_URL = "https://sheets.googleapis.com/v4/spreadsheets/%s/values/'%s'!A1:ZZZ";

    private GssUrls() {}
}
