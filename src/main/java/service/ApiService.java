package service;

import exceptions.BusinessLogicException;
import model.entity.OauthCredentials;

import java.util.List;

public interface ApiService {
    String getAccessToken(OauthCredentials credentials) throws Exception;
    List<String> getTableNames(OauthCredentials credentials) throws BusinessLogicException;
    List<String> getColumnNames(OauthCredentials credentials, String tableName) throws BusinessLogicException;
    void outputCsvForAllData(OauthCredentials credentials, String tableName) throws BusinessLogicException;
}
