import model.entity.OauthCredentials;
import service.ApiService;
import service.GSSApiService;

import static constants.AppConstants.*;

public class Application {
    public static void main(String[] args) {
        OauthCredentials credentials = new OauthCredentials(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);

        ApiService gssApiService = new GSSApiService();
        try {
            gssApiService.outputCsvForAllData(credentials, "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}